package net.sf.briar.transport;

import static java.util.logging.Level.WARNING;
import static net.sf.briar.api.transport.TransportConstants.MAX_CLOCK_DIFFERENCE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TimerTask;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.clock.Timer;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.KeyManager;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.event.ContactRemovedEvent;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.TransportAddedEvent;
import net.sf.briar.api.db.event.TransportRemovedEvent;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionRecogniser;
import net.sf.briar.api.transport.Endpoint;
import net.sf.briar.api.transport.TemporarySecret;
import net.sf.briar.util.ByteUtils;

import com.google.inject.Inject;

// FIXME: Don't make alien calls with a lock held
class KeyManagerImpl extends TimerTask implements KeyManager, DatabaseListener {

	private static final int MS_BETWEEN_CHECKS = 60 * 1000;

	private static final Logger LOG =
			Logger.getLogger(KeyManagerImpl.class.getName());

	private final CryptoComponent crypto;
	private final DatabaseComponent db;
	private final ConnectionRecogniser connectionRecogniser;
	private final Clock clock;
	private final Timer timer;

	// All of the following are locking: this
	private final Map<TransportId, Long> maxLatencies;
	private final Map<EndpointKey, TemporarySecret> outgoing;
	private final Map<EndpointKey, TemporarySecret> incomingOld;
	private final Map<EndpointKey, TemporarySecret> incomingNew;

	@Inject
	KeyManagerImpl(CryptoComponent crypto, DatabaseComponent db,
			ConnectionRecogniser connectionRecogniser, Clock clock,
			Timer timer) {
		this.crypto = crypto;
		this.db = db;
		this.connectionRecogniser = connectionRecogniser;
		this.clock = clock;
		this.timer = timer;
		maxLatencies = new HashMap<TransportId, Long>();
		outgoing = new HashMap<EndpointKey, TemporarySecret>();
		incomingOld = new HashMap<EndpointKey, TemporarySecret>();
		incomingNew = new HashMap<EndpointKey, TemporarySecret>();
	}

	public synchronized boolean start() {
		db.addListener(this);
		// Load the temporary secrets and transport latencies from the database
		Collection<TemporarySecret> secrets;
		try {
			secrets = db.getSecrets();
			maxLatencies.putAll(db.getTransportLatencies());
		} catch(DbException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return false;
		}
		// Work out what phase of its lifecycle each secret is in
		long now = clock.currentTimeMillis();
		Collection<TemporarySecret> dead = assignSecretsToMaps(now, secrets);
		// Replace any dead secrets
		Collection<TemporarySecret> created = replaceDeadSecrets(now, dead);
		if(!created.isEmpty()) {
			// Store any secrets that have been created
			try {
				db.addSecrets(created);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				return false;
			}
		}
		// Pass the current incoming secrets to the recogniser
		for(TemporarySecret s : incomingOld.values())
			connectionRecogniser.addSecret(s);
		for(TemporarySecret s : incomingNew.values())
			connectionRecogniser.addSecret(s);
		// Schedule periodic key rotation
		timer.scheduleAtFixedRate(this, MS_BETWEEN_CHECKS, MS_BETWEEN_CHECKS);
		return true;
	}

	// Assigns secrets to the appropriate maps and returns any dead secrets
	// Locking: this
	private Collection<TemporarySecret> assignSecretsToMaps(long now,
			Collection<TemporarySecret> secrets) {
		Collection<TemporarySecret> dead = new ArrayList<TemporarySecret>();
		for(TemporarySecret s : secrets) {
			// Discard the secret if the transport has been removed
			if(!maxLatencies.containsKey(s.getTransportId())) {
				ByteUtils.erase(s.getSecret());
				continue;
			}
			EndpointKey k = new EndpointKey(s);
			long rotationPeriod = getRotationPeriod(s);
			long creationTime = getCreationTime(s);
			long activationTime = creationTime + MAX_CLOCK_DIFFERENCE;
			long successorCreationTime = creationTime + rotationPeriod;
			long deactivationTime = activationTime + rotationPeriod;
			long destructionTime = successorCreationTime + rotationPeriod;
			if(now >= destructionTime) {
				dead.add(s);
			} else if(now >= deactivationTime) {
				incomingOld.put(k, s);
			} else if(now >= successorCreationTime) {
				incomingOld.put(k, s);
				outgoing.put(k, s);
			} else if(now >= activationTime) {
				incomingNew.put(k, s);
				outgoing.put(k, s);
			} else if(now >= creationTime) {
				incomingNew.put(k, s);
			} else {
				throw new Error("Clock has moved backwards");
			}
		}
		return dead;
	}

	// Replaces and erases the given secrets and returns any secrets created
	// Locking: this
	private Collection<TemporarySecret> replaceDeadSecrets(long now,
			Collection<TemporarySecret> dead) {
		Collection<TemporarySecret> created = new ArrayList<TemporarySecret>();
		for(TemporarySecret s : dead) {
			// Work out which rotation period we're in
			long rotationPeriod = getRotationPeriod(s);
			long elapsed = now - s.getEpoch();
			long period = (elapsed / rotationPeriod) + 1;
			if(period <= s.getPeriod()) throw new IllegalStateException();
			// Derive the two current incoming secrets
			byte[] secret1 = s.getSecret();
			for(long p = s.getPeriod(); p < period; p++) {
				byte[] temp = crypto.deriveNextSecret(secret1, p);
				ByteUtils.erase(secret1);
				secret1 = temp;
			}
			byte[] secret2 = crypto.deriveNextSecret(secret1, period);
			// Add the incoming secrets to their respective maps - the older
			// may already exist if the dead secret has a living successor
			EndpointKey k = new EndpointKey(s);
			TemporarySecret s1 = new TemporarySecret(s, period - 1, secret1);
			created.add(s1);
			TemporarySecret exists = incomingOld.put(k, s1);
			if(exists != null) ByteUtils.erase(exists.getSecret());
			TemporarySecret s2 = new TemporarySecret(s, period, secret2);
			created.add(s2);
			incomingNew.put(k, s2);
			// One of the incoming secrets is the current outgoing secret
			if(elapsed % rotationPeriod < MAX_CLOCK_DIFFERENCE) {
				// The outgoing secret is the older incoming secret
				outgoing.put(k, s1);
			} else {
				// The outgoing secret is the newer incoming secret
				outgoing.put(k, s2);
			}
		}
		return created;
	}

	// Locking: this
	private long getRotationPeriod(Endpoint ep) {
		Long maxLatency = maxLatencies.get(ep.getTransportId());
		if(maxLatency == null) throw new IllegalStateException();
		return 2 * MAX_CLOCK_DIFFERENCE + maxLatency;
	}

	// Locking: this
	private long getCreationTime(TemporarySecret s) {
		long rotationPeriod = getRotationPeriod(s);
		return s.getEpoch() + rotationPeriod * s.getPeriod();
	}

	public synchronized void stop() {
		db.removeListener(this);
		timer.cancel();
		connectionRecogniser.removeSecrets();
		maxLatencies.clear();
		removeAndEraseSecrets(outgoing);
		removeAndEraseSecrets(incomingOld);
		removeAndEraseSecrets(incomingNew);
	}

	// Locking: this
	private void removeAndEraseSecrets(Map<?, TemporarySecret> m) {
		for(TemporarySecret s : m.values()) ByteUtils.erase(s.getSecret());
		m.clear();
	}

	public synchronized ConnectionContext getConnectionContext(ContactId c,
			TransportId t) {
		TemporarySecret s = outgoing.get(new EndpointKey(c, t));
		if(s == null) return null;
		long connection;
		try {
			connection = db.incrementConnectionCounter(c, t, s.getPeriod());
		} catch(DbException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		}
		byte[] secret = s.getSecret().clone();
		return new ConnectionContext(c, t, secret, connection, s.getAlice());
	}

	public synchronized void endpointAdded(Endpoint ep, byte[] initialSecret) {
		if(!maxLatencies.containsKey(ep.getTransportId())) {
			if(LOG.isLoggable(WARNING)) LOG.warning("No such transport");
			return;
		}
		// Work out which rotation period we're in
		long now = clock.currentTimeMillis();
		long rotationPeriod = getRotationPeriod(ep);
		long elapsed = now - ep.getEpoch();
		long period = (elapsed / rotationPeriod) + 1;
		if(period < 1) throw new IllegalStateException();
		// Derive the two current incoming secrets
		byte[] secret1 = initialSecret;
		for(long p = 0; p < period; p++) {
			byte[] temp = crypto.deriveNextSecret(secret1, p);
			ByteUtils.erase(secret1);
			secret1 = temp;
		}
		byte[] secret2 = crypto.deriveNextSecret(secret1, period);
		// Add the incoming secrets to their respective maps
		EndpointKey k = new EndpointKey(ep);
		TemporarySecret s1 = new TemporarySecret(ep, period - 1, secret1);
		incomingOld.put(k, s1);
		TemporarySecret s2 = new TemporarySecret(ep, period, secret2);
		incomingNew.put(k, s2);
		// One of the incoming secrets is the current outgoing secret
		if(elapsed % rotationPeriod < MAX_CLOCK_DIFFERENCE) {
			// The outgoing secret is the older incoming secret
			outgoing.put(k, s1);
		} else {
			// The outgoing secret is the newer incoming secret
			outgoing.put(k, s2);
		}
		// Store the new secrets
		try {
			db.addSecrets(Arrays.asList(s1, s2));
		} catch(DbException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return;
		}
		// Pass the new secrets to the recogniser
		connectionRecogniser.addSecret(s1);
		connectionRecogniser.addSecret(s2);
	}

	@Override
	public synchronized void run() {
		// Rebuild the maps because we may be running a whole period late
		Collection<TemporarySecret> secrets = new ArrayList<TemporarySecret>();
		secrets.addAll(incomingOld.values());
		secrets.addAll(incomingNew.values());
		outgoing.clear();
		incomingOld.clear();
		incomingNew.clear();
		// Work out what phase of its lifecycle each secret is in
		long now = clock.currentTimeMillis();
		Collection<TemporarySecret> dead = assignSecretsToMaps(now, secrets);
		// Remove any dead secrets from the recogniser
		for(TemporarySecret s : dead) {
			ContactId c = s.getContactId();
			TransportId t = s.getTransportId();
			long period = s.getPeriod();
			connectionRecogniser.removeSecret(c, t, period);
		}
		// Replace any dead secrets
		Collection<TemporarySecret> created = replaceDeadSecrets(now, dead);
		if(!created.isEmpty()) {
			// Store any secrets that have been created
			try {
				db.addSecrets(created);
			} catch(DbException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
			// Pass any secrets that have been created to the recogniser
			for(TemporarySecret s : created) connectionRecogniser.addSecret(s);
		}
	}

	public void eventOccurred(DatabaseEvent e) {
		if(e instanceof ContactRemovedEvent) {
			ContactId c = ((ContactRemovedEvent) e).getContactId();
			connectionRecogniser.removeSecrets(c);
			synchronized(this) {
				removeAndEraseSecrets(c, outgoing);
				removeAndEraseSecrets(c, incomingOld);
				removeAndEraseSecrets(c, incomingNew);
			}
		} else if(e instanceof TransportAddedEvent) {
			TransportAddedEvent t = (TransportAddedEvent) e;
			synchronized(this) {
				maxLatencies.put(t.getTransportId(), t.getMaxLatency());
			}
		} else if(e instanceof TransportRemovedEvent) {
			TransportId t = ((TransportRemovedEvent) e).getTransportId();
			connectionRecogniser.removeSecrets(t);
			synchronized(this) {
				maxLatencies.remove(t);
				removeAndEraseSecrets(t, outgoing);
				removeAndEraseSecrets(t, incomingOld);
				removeAndEraseSecrets(t, incomingNew);
			}
		}
	}

	// Locking: this
	private void removeAndEraseSecrets(ContactId c, Map<?, TemporarySecret> m) {
		Iterator<TemporarySecret> it = m.values().iterator();
		while(it.hasNext()) {
			TemporarySecret s = it.next();
			if(s.getContactId().equals(c)) {
				ByteUtils.erase(s.getSecret());
				it.remove();
			}
		}
	}

	// Locking: this
	private void removeAndEraseSecrets(TransportId t,
			Map<?, TemporarySecret> m) {
		Iterator<TemporarySecret> it = m.values().iterator();
		while(it.hasNext()) {
			TemporarySecret s = it.next();
			if(s.getTransportId().equals(t)) {
				ByteUtils.erase(s.getSecret());
				it.remove();
			}
		}
	}

	private static class EndpointKey {

		private final ContactId contactId;
		private final TransportId transportId;

		private EndpointKey(ContactId contactId, TransportId transportId) {
			this.contactId = contactId;
			this.transportId = transportId;
		}

		private EndpointKey(Endpoint ep) {
			this(ep.getContactId(), ep.getTransportId());
		}

		@Override
		public int hashCode() {
			return contactId.hashCode() ^ transportId.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			if(o instanceof EndpointKey) {
				EndpointKey k = (EndpointKey) o;
				return contactId.equals(k.contactId) &&
						transportId.equals(k.transportId);
			}
			return false;
		}
	}
}
