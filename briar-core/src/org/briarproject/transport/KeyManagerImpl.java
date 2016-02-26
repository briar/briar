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
import org.briarproject.api.event.TransportAddedEvent;
import org.briarproject.api.event.TransportRemovedEvent;
import org.briarproject.api.lifecycle.Service;
import org.briarproject.api.system.Clock;
import org.briarproject.api.system.Timer;
import org.briarproject.api.transport.KeyManager;
import org.briarproject.api.transport.StreamContext;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;

class KeyManagerImpl implements KeyManager, Service, EventListener {

	private static final Logger LOG =
			Logger.getLogger(KeyManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final CryptoComponent crypto;
	private final ExecutorService dbExecutor;
	private final Timer timer;
	private final Clock clock;
	private final Map<ContactId, Boolean> activeContacts;
	private final ConcurrentHashMap<TransportId, TransportKeyManager> managers;

	@Inject
	KeyManagerImpl(DatabaseComponent db, CryptoComponent crypto,
			@DatabaseExecutor ExecutorService dbExecutor, Timer timer,
			Clock clock) {
		this.db = db;
		this.crypto = crypto;
		this.dbExecutor = dbExecutor;
		this.timer = timer;
		this.clock = clock;
		// Use a ConcurrentHashMap as a thread-safe set
		activeContacts = new ConcurrentHashMap<ContactId, Boolean>();
		managers = new ConcurrentHashMap<TransportId, TransportKeyManager>();
	}

	@Override
	public boolean start() {
		try {
			Collection<Contact> contacts;
			Map<TransportId, Integer> latencies;
			Transaction txn = db.startTransaction();
			try {
				contacts = db.getContacts(txn);
				latencies = db.getTransportLatencies(txn);
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
			for (Contact c : contacts)
				if (c.isActive()) activeContacts.put(c.getId(), true);
			for (Entry<TransportId, Integer> e : latencies.entrySet())
				addTransport(e.getKey(), e.getValue());
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return false;
		}
		return true;
	}

	@Override
	public boolean stop() {
		return true;
	}

	public void addContact(Transaction txn, ContactId c, SecretKey master,
			long timestamp, boolean alice) throws DbException {
		for (TransportKeyManager m : managers.values())
			m.addContact(txn, c, master, timestamp, alice);
	}

	public StreamContext getStreamContext(ContactId c, TransportId t) {
		// Don't allow outgoing streams to inactive contacts
		if (!activeContacts.containsKey(c)) return null;
		TransportKeyManager m = managers.get(t);
		return m == null ? null : m.getStreamContext(c);
	}

	public StreamContext getStreamContext(TransportId t, byte[] tag) {
		TransportKeyManager m = managers.get(t);
		if (m == null) return null;
		StreamContext ctx = m.getStreamContext(tag);
		if (ctx == null) return null;
		// Activate the contact if not already active
		if (!activeContacts.containsKey(ctx.getContactId())) {
			try {
				Transaction txn = db.startTransaction();
				try {
					db.setContactActive(txn, ctx.getContactId(), true);
					txn.setComplete();
				} finally {
					db.endTransaction(txn);
				}
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				return null;
			}
		}
		return ctx;
	}

	public void eventOccurred(Event e) {
		if (e instanceof TransportAddedEvent) {
			TransportAddedEvent t = (TransportAddedEvent) e;
			addTransport(t.getTransportId(), t.getMaxLatency());
		} else if (e instanceof TransportRemovedEvent) {
			removeTransport(((TransportRemovedEvent) e).getTransportId());
		} else if (e instanceof ContactRemovedEvent) {
			removeContact(((ContactRemovedEvent) e).getContactId());
		} else if (e instanceof ContactStatusChangedEvent) {
			ContactStatusChangedEvent c = (ContactStatusChangedEvent) e;
			if (c.isActive()) activeContacts.put(c.getContactId(), true);
			else activeContacts.remove(c.getContactId());
		}
	}

	private void addTransport(final TransportId t, final int maxLatency) {
		dbExecutor.execute(new Runnable() {
			public void run() {
				TransportKeyManager m = new TransportKeyManager(db, crypto,
						timer, clock, t, maxLatency);
				// Don't add transport twice if event is received during startup
				if (managers.putIfAbsent(t, m) == null) m.start();
			}
		});
	}

	private void removeTransport(TransportId t) {
		managers.remove(t);
	}

	private void removeContact(final ContactId c) {
		activeContacts.remove(c);
		dbExecutor.execute(new Runnable() {
			public void run() {
				for (TransportKeyManager m : managers.values())
					m.removeContact(c);
			}
		});
	}
}
