package org.briarproject.bramble.transport;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.event.ContactRemovedEvent;
import org.briarproject.bramble.api.contact.event.ContactStatusChangedEvent;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.Service;
import org.briarproject.bramble.api.lifecycle.ServiceException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginConfig;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginFactory;
import org.briarproject.bramble.api.transport.KeyManager;
import org.briarproject.bramble.api.transport.StreamContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

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
	private final Map<ContactId, Boolean> activeContacts;
	private final ConcurrentHashMap<TransportId, TransportKeyManager> managers;
	private final AtomicBoolean used = new AtomicBoolean(false);

	@Inject
	KeyManagerImpl(DatabaseComponent db, @DatabaseExecutor Executor dbExecutor,
			PluginConfig pluginConfig,
			TransportKeyManagerFactory transportKeyManagerFactory) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.pluginConfig = pluginConfig;
		this.transportKeyManagerFactory = transportKeyManagerFactory;
		// Use a ConcurrentHashMap as a thread-safe set
		activeContacts = new ConcurrentHashMap<ContactId, Boolean>();
		managers = new ConcurrentHashMap<TransportId, TransportKeyManager>();
	}

	@Override
	public void startService() throws ServiceException {
		if (used.getAndSet(true)) throw new IllegalStateException();
		Map<TransportId, Integer> transports =
				new HashMap<TransportId, Integer>();
		for (SimplexPluginFactory f : pluginConfig.getSimplexFactories())
			transports.put(f.getId(), f.getMaxLatency());
		for (DuplexPluginFactory f : pluginConfig.getDuplexFactories())
			transports.put(f.getId(), f.getMaxLatency());
		try {
			Transaction txn = db.startTransaction(false);
			try {
				for (Contact c : db.getContacts(txn))
					if (c.isActive()) activeContacts.put(c.getId(), true);
				for (Entry<TransportId, Integer> e : transports.entrySet())
					db.addTransport(txn, e.getKey(), e.getValue());
				for (Entry<TransportId, Integer> e : transports.entrySet()) {
					TransportKeyManager m = transportKeyManagerFactory
							.createTransportKeyManager(e.getKey(),
									e.getValue());
					managers.put(e.getKey(), m);
					m.start(txn);
				}
				db.commitTransaction(txn);
			} finally {
				db.endTransaction(txn);
			}
		} catch (DbException e) {
			throw new ServiceException(e);
		}
	}

	@Override
	public void stopService() {
	}

	@Override
	public void addContact(Transaction txn, ContactId c, SecretKey master,
			long timestamp, boolean alice) throws DbException {
		for (TransportKeyManager m : managers.values())
			m.addContact(txn, c, master, timestamp, alice);
	}

	@Override
	public StreamContext getStreamContext(ContactId c, TransportId t)
			throws DbException {
		// Don't allow outgoing streams to inactive contacts
		if (!activeContacts.containsKey(c)) return null;
		TransportKeyManager m = managers.get(t);
		if (m == null) {
			if (LOG.isLoggable(INFO)) LOG.info("No key manager for " + t);
			return null;
		}
		StreamContext ctx = null;
		Transaction txn = db.startTransaction(false);
		try {
			ctx = m.getStreamContext(txn, c);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
		return ctx;
	}

	@Override
	public StreamContext getStreamContext(TransportId t, byte[] tag)
			throws DbException {
		TransportKeyManager m = managers.get(t);
		if (m == null) {
			if (LOG.isLoggable(INFO)) LOG.info("No key manager for " + t);
			return null;
		}
		StreamContext ctx = null;
		Transaction txn = db.startTransaction(false);
		try {
			ctx = m.getStreamContext(txn, tag);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
		return ctx;
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ContactRemovedEvent) {
			removeContact(((ContactRemovedEvent) e).getContactId());
		} else if (e instanceof ContactStatusChangedEvent) {
			ContactStatusChangedEvent c = (ContactStatusChangedEvent) e;
			if (c.isActive()) activeContacts.put(c.getContactId(), true);
			else activeContacts.remove(c.getContactId());
		}
	}

	private void removeContact(final ContactId c) {
		activeContacts.remove(c);
		dbExecutor.execute(new Runnable() {
			@Override
			public void run() {
				for (TransportKeyManager m : managers.values())
					m.removeContact(c);
			}
		});
	}
}
