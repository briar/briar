package org.briarproject.transport;

import org.briarproject.api.TransportId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.ContactStatusChangedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.lifecycle.Service;
import org.briarproject.api.lifecycle.ServiceException;
import org.briarproject.api.plugins.PluginConfig;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;
import org.briarproject.api.plugins.simplex.SimplexPluginFactory;
import org.briarproject.api.system.Clock;
import org.briarproject.api.transport.KeyManager;
import org.briarproject.api.transport.StreamContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;

class KeyManagerImpl implements KeyManager, Service, EventListener {

	private static final Logger LOG =
			Logger.getLogger(KeyManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final CryptoComponent crypto;
	private final Executor dbExecutor;
	private final ScheduledExecutorService scheduler;
	private final PluginConfig pluginConfig;
	private final Clock clock;
	private final Map<ContactId, Boolean> activeContacts;
	private final ConcurrentHashMap<TransportId, TransportKeyManager> managers;

	@Inject
	KeyManagerImpl(DatabaseComponent db, CryptoComponent crypto,
			@DatabaseExecutor Executor dbExecutor,
			ScheduledExecutorService scheduler, PluginConfig pluginConfig,
			Clock clock) {
		this.db = db;
		this.crypto = crypto;
		this.dbExecutor = dbExecutor;
		this.scheduler = scheduler;
		this.pluginConfig = pluginConfig;
		this.clock = clock;
		// Use a ConcurrentHashMap as a thread-safe set
		activeContacts = new ConcurrentHashMap<ContactId, Boolean>();
		managers = new ConcurrentHashMap<TransportId, TransportKeyManager>();
	}

	@Override
	public void startService() throws ServiceException {
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
					TransportKeyManager m = new TransportKeyManager(db, crypto,
							dbExecutor, scheduler, clock, e.getKey(),
							e.getValue());
					managers.put(e.getKey(), m);
					m.start(txn);
				}
				txn.setComplete();
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
			txn.setComplete();
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
			txn.setComplete();
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
