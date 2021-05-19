package org.briarproject.bramble.transport;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.contact.event.ContactRemovedEvent;
import org.briarproject.bramble.api.contact.event.PendingContactRemovedEvent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.crypto.TransportCrypto;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventExecutor;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.Service;
import org.briarproject.bramble.api.lifecycle.ServiceException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginConfig;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginFactory;
import org.briarproject.bramble.api.transport.KeyManager;
import org.briarproject.bramble.api.transport.KeySetId;
import org.briarproject.bramble.api.transport.StreamContext;

import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.util.logging.Level.INFO;

@ThreadSafe
@NotNullByDefault
class KeyManagerImpl implements KeyManager, Service, EventListener {

	private static final Logger LOG =
			Logger.getLogger(KeyManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final PluginConfig pluginConfig;
	private final TransportKeyManagerFactory transportKeyManagerFactory;
	private final TransportCrypto transportCrypto;

	private final ConcurrentHashMap<TransportId, TransportKeyManager> managers;
	private final AtomicBoolean used = new AtomicBoolean(false);

	@Inject
	KeyManagerImpl(DatabaseComponent db,
			@DatabaseExecutor Executor dbExecutor,
			PluginConfig pluginConfig,
			TransportKeyManagerFactory transportKeyManagerFactory,
			TransportCrypto transportCrypto) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.pluginConfig = pluginConfig;
		this.transportKeyManagerFactory = transportKeyManagerFactory;
		this.transportCrypto = transportCrypto;
		managers = new ConcurrentHashMap<>();
	}

	@Override
	public void startService() throws ServiceException {
		if (used.getAndSet(true)) throw new IllegalStateException();
		Map<TransportId, Integer> transports = new HashMap<>();
		for (SimplexPluginFactory f : pluginConfig.getSimplexFactories())
			transports.put(f.getId(), f.getMaxLatency());
		for (DuplexPluginFactory f : pluginConfig.getDuplexFactories())
			transports.put(f.getId(), f.getMaxLatency());
		try {
			db.transaction(false, txn -> {
				for (Entry<TransportId, Integer> e : transports.entrySet())
					db.addTransport(txn, e.getKey(), e.getValue());
				for (Entry<TransportId, Integer> e : transports.entrySet()) {
					TransportKeyManager m = transportKeyManagerFactory
							.createTransportKeyManager(e.getKey(),
									e.getValue());
					managers.put(e.getKey(), m);
					m.start(txn);
				}
			});
		} catch (DbException e) {
			throw new ServiceException(e);
		}
	}

	@Override
	public void stopService() {
	}

	@Override
	public KeySetId addRotationKeys(Transaction txn, ContactId c,
			TransportId t, SecretKey rootKey, long timestamp, boolean alice,
			boolean active) throws DbException {
		return withManager(t, m ->
				m.addRotationKeys(txn, c, rootKey, timestamp, alice, active));
	}

	@Override
	public Map<TransportId, KeySetId> addRotationKeys(Transaction txn,
			ContactId c, SecretKey rootKey, long timestamp, boolean alice,
			boolean active) throws DbException {
		Map<TransportId, KeySetId> ids = new HashMap<>();
		for (Entry<TransportId, TransportKeyManager> e : managers.entrySet()) {
			TransportId t = e.getKey();
			TransportKeyManager m = e.getValue();
			ids.put(t, m.addRotationKeys(txn, c, rootKey, timestamp,
					alice, active));
		}
		return ids;
	}

	@Override
	public Map<TransportId, KeySetId> addContact(Transaction txn, ContactId c,
			PublicKey theirPublicKey, KeyPair ourKeyPair)
			throws DbException, GeneralSecurityException {
		SecretKey staticMasterKey = transportCrypto
				.deriveStaticMasterKey(theirPublicKey, ourKeyPair);
		SecretKey rootKey =
				transportCrypto.deriveHandshakeRootKey(staticMasterKey, false);
		boolean alice = transportCrypto.isAlice(theirPublicKey, ourKeyPair);
		Map<TransportId, KeySetId> ids = new HashMap<>();
		for (Entry<TransportId, TransportKeyManager> e : managers.entrySet()) {
			TransportId t = e.getKey();
			TransportKeyManager m = e.getValue();
			ids.put(t, m.addHandshakeKeys(txn, c, rootKey, alice));
		}
		return ids;
	}

	@Override
	public Map<TransportId, KeySetId> addPendingContact(Transaction txn,
			PendingContactId p, PublicKey theirPublicKey, KeyPair ourKeyPair)
			throws DbException, GeneralSecurityException {
		SecretKey staticMasterKey = transportCrypto
				.deriveStaticMasterKey(theirPublicKey, ourKeyPair);
		SecretKey rootKey =
				transportCrypto.deriveHandshakeRootKey(staticMasterKey, true);
		boolean alice = transportCrypto.isAlice(theirPublicKey, ourKeyPair);
		Map<TransportId, KeySetId> ids = new HashMap<>();
		for (Entry<TransportId, TransportKeyManager> e : managers.entrySet()) {
			TransportId t = e.getKey();
			TransportKeyManager m = e.getValue();
			ids.put(t, m.addHandshakeKeys(txn, p, rootKey, alice));
		}
		return ids;
	}

	@Override
	public void activateKeys(Transaction txn, Map<TransportId, KeySetId> keys)
			throws DbException {
		for (Entry<TransportId, KeySetId> e : keys.entrySet()) {
			withManager(e.getKey(), m -> {
				m.activateKeys(txn, e.getValue());
				return null;
			});
		}
	}

	@Override
	public boolean canSendOutgoingStreams(ContactId c, TransportId t) {
		TransportKeyManager m = managers.get(t);
		return m != null && m.canSendOutgoingStreams(c);
	}

	@Override
	public boolean canSendOutgoingStreams(PendingContactId p, TransportId t) {
		TransportKeyManager m = managers.get(t);
		return m != null && m.canSendOutgoingStreams(p);
	}

	@Override
	public StreamContext getStreamContext(ContactId c, TransportId t)
			throws DbException {
		return withManager(t, m ->
				db.transactionWithNullableResult(false, txn ->
						m.getStreamContext(txn, c)));
	}

	@Override
	public StreamContext getStreamContext(PendingContactId p, TransportId t)
			throws DbException {
		return withManager(t, m ->
				db.transactionWithNullableResult(false, txn ->
						m.getStreamContext(txn, p)));
	}

	@Override
	public StreamContext getStreamContext(TransportId t, byte[] tag)
			throws DbException {
		return withManager(t, m ->
				db.transactionWithNullableResult(false, txn ->
						m.getStreamContext(txn, tag)));
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ContactRemovedEvent) {
			removeContact(((ContactRemovedEvent) e).getContactId());
		} else if (e instanceof PendingContactRemovedEvent) {
			PendingContactRemovedEvent p = (PendingContactRemovedEvent) e;
			removePendingContact(p.getId());
		}
	}

	@EventExecutor
	private void removeContact(ContactId c) {
		dbExecutor.execute(() -> {
			for (TransportKeyManager m : managers.values()) m.removeContact(c);
		});
	}

	@EventExecutor
	private void removePendingContact(PendingContactId p) {
		dbExecutor.execute(() -> {
			for (TransportKeyManager m : managers.values())
				m.removePendingContact(p);
		});
	}

	@Nullable
	private <T> T withManager(TransportId t, ManagerTask<T> task)
			throws DbException {
		TransportKeyManager m = managers.get(t);
		if (m == null) {
			if (LOG.isLoggable(INFO)) LOG.info("No key manager for " + t);
			return null;
		}
		return task.run(m);
	}

	private interface ManagerTask<T> {
		@Nullable
		T run(TransportKeyManager m) throws DbException;
	}
}
