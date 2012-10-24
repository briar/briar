package net.sf.briar.transport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.KeyManager;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.TemporarySecret;
import net.sf.briar.api.db.event.ContactRemovedEvent;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionRecogniser;
import net.sf.briar.util.ByteUtils;

import com.google.inject.Inject;

// FIXME: When a contact transport is added we need to load its secrets

class KeyManagerImpl extends TimerTask implements KeyManager, DatabaseListener {

	private static final int MS_BETWEEN_CHECKS = 60 * 1000;

	private static final Logger LOG =
			Logger.getLogger(KeyManagerImpl.class.getName());

	private final CryptoComponent crypto;
	private final DatabaseComponent db;
	private final ConnectionRecogniser recogniser;
	private final Timer timer;
	// Locking: this
	private final Map<ContactTransportKey, TemporarySecret> outgoing;
	// Locking: this
	private final Map<ContactTransportKey, TemporarySecret> incomingOld;
	// Locking: this
	private final Map<ContactTransportKey, TemporarySecret> incomingNew;

	@Inject
	public KeyManagerImpl(CryptoComponent crypto, DatabaseComponent db,
			ConnectionRecogniser recogniser) {
		this.crypto = crypto;
		this.db = db;
		this.recogniser = recogniser;
		timer = new Timer();
		outgoing = new HashMap<ContactTransportKey, TemporarySecret>();
		incomingOld = new HashMap<ContactTransportKey, TemporarySecret>();
		incomingNew = new HashMap<ContactTransportKey, TemporarySecret>();
	}

	public synchronized boolean start() {
		Collection<TemporarySecret> secrets;
		try {
			secrets = db.getSecrets();
		} catch(DbException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
			return false;
		}
		// Work out what phase of its lifecycle each secret is in
		long now = System.currentTimeMillis();
		Collection<TemporarySecret> dead = assignSecretsToMaps(now, secrets);
		// Replace any dead secrets
		Collection<TemporarySecret> created = replaceDeadSecrets(now, dead);
		if(!created.isEmpty()) {
			// Store any secrets that have been created
			try {
				db.addSecrets(created);
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
				return false;
			}
		}
		// Pass the current incoming secrets to the recogniser
		for(TemporarySecret s : incomingOld.values()) recogniser.addSecret(s);
		for(TemporarySecret s : incomingNew.values()) recogniser.addSecret(s);
		// Schedule periodic key rotation
		timer.scheduleAtFixedRate(this, MS_BETWEEN_CHECKS, MS_BETWEEN_CHECKS);
		return true;
	}

	// Assigns secrets to the appropriate maps and returns any dead secrets
	// FIXME: Check there are no duplicate keys when updating maps
	private Collection<TemporarySecret> assignSecretsToMaps(long now,
			Collection<TemporarySecret> secrets) {
		Collection<TemporarySecret> dead = new ArrayList<TemporarySecret>();
		for(TemporarySecret s : secrets) {
			ContactId c = s.getContactId();
			TransportId t = s.getTransportId();
			ContactTransportKey k = new ContactTransportKey(c, t);
			long rotationPeriod = getRotationPeriod(s);
			long creationTime = getCreationTime(s);
			long activationTime = creationTime + s.getClockDifference();
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
				// FIXME: What should we do if the clock moves backwards?
				throw new IllegalStateException();
			}
		}
		return dead;
	}

	// Replaces and erases the given secrets and returns any secrets created
	private Collection<TemporarySecret> replaceDeadSecrets(long now,
			Collection<TemporarySecret> dead) {
		Collection<TemporarySecret> created = new ArrayList<TemporarySecret>();
		for(TemporarySecret s : dead) {
			ContactId c = s.getContactId();
			TransportId t = s.getTransportId();
			ContactTransportKey k = new ContactTransportKey(c, t);
			if(incomingNew.containsKey(k)) throw new IllegalStateException();
			byte[] secret = s.getSecret();
			long period = s.getPeriod();
			if(incomingOld.containsKey(k)) {
				// The dead secret's successor is still alive
				byte[] secret1 = crypto.deriveNextSecret(secret, period + 1);
				TemporarySecret s1 = new TemporarySecret(s, period + 1,
						secret1);
				created.add(s1);
				incomingNew.put(k, s1);
				long creationTime = getCreationTime(s1);
				long activationTime = creationTime + s1.getClockDifference();
				if(now >= activationTime) outgoing.put(k, s1);
			} else  {
				// The dead secret has no living successor
				long rotationPeriod = getRotationPeriod(s);
				long elapsed = now - s.getEpoch();
				long currentPeriod = elapsed / rotationPeriod;
				if(currentPeriod <= period) throw new IllegalStateException();
				// Derive the two current incoming secrets
				byte[] secret1, secret2;
				secret1 = secret;
				for(long l = period; l < currentPeriod; l++) {
					byte[] temp = crypto.deriveNextSecret(secret1, l);
					ByteUtils.erase(secret1);
					secret1 = temp;
				}
				secret2 = crypto.deriveNextSecret(secret1, currentPeriod);
				// One of the incoming secrets is the current outgoing secret
				TemporarySecret s1, s2;
				s1 = new TemporarySecret(s, currentPeriod - 1, secret1);
				created.add(s1);
				incomingOld.put(k, s1);
				s2 = new TemporarySecret(s, currentPeriod, secret2);
				created.add(s2);
				incomingNew.put(k, s2);
				if(elapsed % rotationPeriod < s.getClockDifference()) {
					// The outgoing secret is the newer incoming secret
					outgoing.put(k, s2);
				} else {
					// The outgoing secret is the older incoming secret
					outgoing.put(k, s1);
				}
			}
			// Erase the dead secret
			ByteUtils.erase(secret);
		}
		return created;
	}

	private long getRotationPeriod(TemporarySecret s) {
		return 2 * s.getClockDifference() + s.getLatency();
	}

	private long getCreationTime(TemporarySecret s) {
		long rotationPeriod = getRotationPeriod(s);
		return s.getEpoch() + rotationPeriod * s.getPeriod();
	}

	public synchronized void stop() {
		timer.cancel();
		recogniser.removeSecrets();
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
		TemporarySecret s = outgoing.get(new ContactTransportKey(c, t));
		if(s == null) return null;
		long connection;
		try {
			connection = db.incrementConnectionCounter(c, t, s.getPeriod());
		} catch(DbException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
			return null;
		}
		byte[] secret = s.getSecret().clone();
		return new ConnectionContext(c, t, secret, connection, s.getAlice());
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
		long now = System.currentTimeMillis();
		Collection<TemporarySecret> dead = assignSecretsToMaps(now, secrets);
		// Remove any dead secrets from the recogniser
		for(TemporarySecret s : dead) {
			ContactId c = s.getContactId();
			TransportId t = s.getTransportId();
			long period = s.getPeriod();
			recogniser.removeSecret(c, t, period);
		}
		// Replace any dead secrets
		Collection<TemporarySecret> created = replaceDeadSecrets(now, dead);
		if(!created.isEmpty()) {
			// Store any secrets that have been created
			try {
				db.addSecrets(created);
			} catch(DbException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
			}
			// Pass any secrets that have been created to the recogniser
			for(TemporarySecret s : created) recogniser.addSecret(s);
		}
	}

	public void eventOccurred(DatabaseEvent e) {
		if(e instanceof ContactRemovedEvent) {
			ContactId c = ((ContactRemovedEvent) e).getContactId();
			recogniser.removeSecrets(c);
			synchronized(this) {
				removeAndEraseSecrets(c, outgoing);
				removeAndEraseSecrets(c, incomingOld);
				removeAndEraseSecrets(c, incomingNew);
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

	private static class ContactTransportKey {

		private final ContactId contactId;
		private final TransportId transportId;

		private ContactTransportKey(ContactId contactId,
				TransportId transportId) {
			this.contactId = contactId;
			this.transportId = transportId;
		}

		@Override
		public int hashCode() {
			return contactId.hashCode() + transportId.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			if(o instanceof ContactTransportKey) {
				ContactTransportKey k = (ContactTransportKey) o;
				return contactId.equals(k.contactId) &&
						transportId.equals(k.transportId);
			}
			return false;
		}
	}
}
