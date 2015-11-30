package org.briarproject.db;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.db.DatabaseConstants.BYTES_PER_SWEEP;
import static org.briarproject.db.DatabaseConstants.CRITICAL_FREE_SPACE;
import static org.briarproject.db.DatabaseConstants.MAX_OFFERED_MESSAGES;
import static org.briarproject.db.DatabaseConstants.MAX_TRANSACTIONS_BETWEEN_SPACE_CHECKS;
import static org.briarproject.db.DatabaseConstants.MIN_FREE_SPACE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.api.Author;
import org.briarproject.api.AuthorId;
import org.briarproject.api.Contact;
import org.briarproject.api.ContactId;
import org.briarproject.api.LocalAuthor;
import org.briarproject.api.Settings;
import org.briarproject.api.TransportConfig;
import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.db.ContactExistsException;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.LocalAuthorExistsException;
import org.briarproject.api.db.MessageHeader;
import org.briarproject.api.db.NoSuchContactException;
import org.briarproject.api.db.NoSuchLocalAuthorException;
import org.briarproject.api.db.NoSuchMessageException;
import org.briarproject.api.db.NoSuchSubscriptionException;
import org.briarproject.api.db.NoSuchTransportException;
import org.briarproject.api.event.ContactAddedEvent;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.LocalAuthorAddedEvent;
import org.briarproject.api.event.LocalAuthorRemovedEvent;
import org.briarproject.api.event.LocalSubscriptionsUpdatedEvent;
import org.briarproject.api.event.LocalTransportsUpdatedEvent;
import org.briarproject.api.event.MessageAddedEvent;
import org.briarproject.api.event.MessageExpiredEvent;
import org.briarproject.api.event.MessageRequestedEvent;
import org.briarproject.api.event.MessageToAckEvent;
import org.briarproject.api.event.MessageToRequestEvent;
import org.briarproject.api.event.MessagesAckedEvent;
import org.briarproject.api.event.RemoteRetentionTimeUpdatedEvent;
import org.briarproject.api.event.RemoteSubscriptionsUpdatedEvent;
import org.briarproject.api.event.RemoteTransportsUpdatedEvent;
import org.briarproject.api.event.SettingsUpdatedEvent;
import org.briarproject.api.event.SubscriptionAddedEvent;
import org.briarproject.api.event.SubscriptionRemovedEvent;
import org.briarproject.api.event.TransportAddedEvent;
import org.briarproject.api.event.TransportRemovedEvent;
import org.briarproject.api.lifecycle.ShutdownManager;
import org.briarproject.api.messaging.Ack;
import org.briarproject.api.messaging.Group;
import org.briarproject.api.messaging.GroupId;
import org.briarproject.api.messaging.Message;
import org.briarproject.api.messaging.MessageId;
import org.briarproject.api.messaging.Offer;
import org.briarproject.api.messaging.Request;
import org.briarproject.api.messaging.RetentionAck;
import org.briarproject.api.messaging.RetentionUpdate;
import org.briarproject.api.messaging.SubscriptionAck;
import org.briarproject.api.messaging.SubscriptionUpdate;
import org.briarproject.api.messaging.TransportAck;
import org.briarproject.api.messaging.TransportUpdate;
import org.briarproject.api.transport.Endpoint;
import org.briarproject.api.transport.TemporarySecret;

/**
 * An implementation of DatabaseComponent using reentrant read-write locks.
 * Depending on the JVM's lock implementation, this implementation may allow
 * writers to starve. LockFairnessTest can be used to test whether this
 * implementation is safe on a given JVM.
 */
class DatabaseComponentImpl<T> implements DatabaseComponent,
DatabaseCleaner.Callback {

	private static final Logger LOG =
			Logger.getLogger(DatabaseComponentImpl.class.getName());
	private static final int MS_BETWEEN_SWEEPS = 10 * 1000; // 10 seconds

	private final Database<T> db;
	private final DatabaseCleaner cleaner;
	private final EventBus eventBus;
	private final ShutdownManager shutdown;

	private final ReentrantReadWriteLock lock =
			new ReentrantReadWriteLock(true);

	private boolean open = false; // Locking: lock.writeLock
	private int shutdownHandle = -1; // Locking: lock.writeLock

	@Inject
	DatabaseComponentImpl(Database<T> db, DatabaseCleaner cleaner,
			EventBus eventBus, ShutdownManager shutdown) {
		this.db = db;
		this.cleaner = cleaner;
		this.eventBus = eventBus;
		this.shutdown = shutdown;
	}

	public boolean open() throws DbException, IOException {
		Runnable shutdownHook = new Runnable() {
			public void run() {
				lock.writeLock().lock();
				try {
					shutdownHandle = -1;
					close();
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch (IOException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} finally {
					lock.writeLock().unlock();
				}
			}
		};
		lock.writeLock().lock();
		try {
			if (open) throw new IllegalStateException();
			open = true;
			boolean reopened = db.open();
			cleaner.startCleaning(this, MS_BETWEEN_SWEEPS);
			shutdownHandle = shutdown.addShutdownHook(shutdownHook);
			return reopened;
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void close() throws DbException, IOException {
		lock.writeLock().lock();
		try {
			if (!open) return;
			open = false;
			if (shutdownHandle != -1)
				shutdown.removeShutdownHook(shutdownHandle);
			cleaner.stopCleaning();
			db.close();
		} finally {
			lock.writeLock().unlock();
		}
	}

	public ContactId addContact(Author remote, AuthorId local)
			throws DbException {
		ContactId c;
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (db.containsContact(txn, remote.getId()))
					throw new ContactExistsException();
				if (!db.containsLocalAuthor(txn, local))
					throw new NoSuchLocalAuthorException();
				c = db.addContact(txn, remote, local);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		eventBus.broadcast(new ContactAddedEvent(c));
		return c;
	}

	public void addEndpoint(Endpoint ep) throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, ep.getContactId()))
					throw new NoSuchContactException();
				if (!db.containsTransport(txn, ep.getTransportId()))
					throw new NoSuchTransportException();
				db.addEndpoint(txn, ep);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public boolean addGroup(Group g) throws DbException {
		boolean added = false;
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsGroup(txn, g.getId()))
					added = db.addGroup(txn, g);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		if (added) eventBus.broadcast(new SubscriptionAddedEvent(g));
		return added;
	}

	public void addLocalAuthor(LocalAuthor a) throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (db.containsLocalAuthor(txn, a.getId()))
					throw new LocalAuthorExistsException();
				db.addLocalAuthor(txn, a);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		eventBus.broadcast(new LocalAuthorAddedEvent(a.getId()));
	}

	public void addLocalMessage(Message m) throws DbException {
		boolean duplicate, subscribed;
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				duplicate = db.containsMessage(txn, m.getId());
				subscribed = db.containsGroup(txn, m.getGroup().getId());
				if (!duplicate && subscribed) addMessage(txn, m, null);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		if (!duplicate && subscribed)
			eventBus.broadcast(new MessageAddedEvent(m.getGroup(), null));
	}

	/**
	 * Stores a message, initialises its status with respect to each contact,
	 * and marks it as read if it was locally generated.
	 * <p>
	 * Locking: write.
	 * @param sender null for a locally generated message.
	 */
	private void addMessage(T txn, Message m, ContactId sender)
			throws DbException {
		if (sender == null) {
			db.addMessage(txn, m, true);
			db.setReadFlag(txn, m.getId(), true);
		} else {
			db.addMessage(txn, m, false);
		}
		Group g = m.getGroup();
		Collection<ContactId> visibility = db.getVisibility(txn, g.getId());
		visibility = new HashSet<ContactId>(visibility);
		for (ContactId c : db.getContactIds(txn)) {
			if (visibility.contains(c)) {
				boolean offered = db.removeOfferedMessage(txn, c, m.getId());
				boolean seen = offered || c.equals(sender);
				db.addStatus(txn, c, m.getId(), offered, seen);
			} else {
				if (c.equals(sender)) throw new IllegalStateException();
				db.addStatus(txn, c, m.getId(), false, false);
			}
		}
	}

	public void addSecrets(Collection<TemporarySecret> secrets)
			throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Collection<TemporarySecret> relevant =
						new ArrayList<TemporarySecret>();
				for (TemporarySecret s : secrets) {
					if (db.containsContact(txn, s.getContactId()))
						if (db.containsTransport(txn, s.getTransportId()))
							relevant.add(s);
				}
				if (!secrets.isEmpty()) db.addSecrets(txn, relevant);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public boolean addTransport(TransportId t, int maxLatency)
			throws DbException {
		boolean added;
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				added = db.addTransport(txn, t, maxLatency);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		if (added) eventBus.broadcast(new TransportAddedEvent(t, maxLatency));
		return added;
	}

	public Ack generateAck(ContactId c, int maxMessages) throws DbException {
		Collection<MessageId> ids;
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				ids = db.getMessagesToAck(txn, c, maxMessages);
				if (!ids.isEmpty()) db.lowerAckFlag(txn, c, ids);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		if (ids.isEmpty()) return null;
		return new Ack(ids);
	}

	public Collection<byte[]> generateBatch(ContactId c, int maxLength,
			int maxLatency) throws DbException {
		Collection<MessageId> ids;
		List<byte[]> messages = new ArrayList<byte[]>();
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				ids = db.getMessagesToSend(txn, c, maxLength);
				for (MessageId m : ids) {
					messages.add(db.getRawMessage(txn, m));
					db.updateExpiryTime(txn, c, m, maxLatency);
				}
				if (!ids.isEmpty()) db.lowerRequestedFlag(txn, c, ids);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		if (messages.isEmpty()) return null;
		return Collections.unmodifiableList(messages);
	}

	public Offer generateOffer(ContactId c, int maxMessages, int maxLatency)
			throws DbException {
		Collection<MessageId> ids;
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				ids = db.getMessagesToOffer(txn, c, maxMessages);
				for (MessageId m : ids)
					db.updateExpiryTime(txn, c, m, maxLatency);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		if (ids.isEmpty()) return null;
		return new Offer(ids);
	}

	public Request generateRequest(ContactId c, int maxMessages)
			throws DbException {
		Collection<MessageId> ids;
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				ids = db.getMessagesToRequest(txn, c, maxMessages);
				if (!ids.isEmpty()) db.removeOfferedMessages(txn, c, ids);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		if (ids.isEmpty()) return null;
		return new Request(ids);
	}

	public Collection<byte[]> generateRequestedBatch(ContactId c, int maxLength,
			int maxLatency) throws DbException {
		Collection<MessageId> ids;
		List<byte[]> messages = new ArrayList<byte[]>();
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				ids = db.getRequestedMessagesToSend(txn, c, maxLength);
				for (MessageId m : ids) {
					messages.add(db.getRawMessage(txn, m));
					db.updateExpiryTime(txn, c, m, maxLatency);
				}
				if (!ids.isEmpty()) db.lowerRequestedFlag(txn, c, ids);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		if (messages.isEmpty()) return null;
		return Collections.unmodifiableList(messages);
	}

	public RetentionAck generateRetentionAck(ContactId c) throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				RetentionAck a = db.getRetentionAck(txn, c);
				db.commitTransaction(txn);
				return a;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public RetentionUpdate generateRetentionUpdate(ContactId c, int maxLatency)
			throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				RetentionUpdate u = db.getRetentionUpdate(txn, c, maxLatency);
				db.commitTransaction(txn);
				return u;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public SubscriptionAck generateSubscriptionAck(ContactId c)
			throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				SubscriptionAck a = db.getSubscriptionAck(txn, c);
				db.commitTransaction(txn);
				return a;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public SubscriptionUpdate generateSubscriptionUpdate(ContactId c,
			int maxLatency) throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				SubscriptionUpdate u =
						db.getSubscriptionUpdate(txn, c, maxLatency);
				db.commitTransaction(txn);
				return u;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public Collection<TransportAck> generateTransportAcks(ContactId c)
			throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				Collection<TransportAck> acks = db.getTransportAcks(txn, c);
				db.commitTransaction(txn);
				return acks;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public Collection<TransportUpdate> generateTransportUpdates(ContactId c,
			int maxLatency) throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				Collection<TransportUpdate> updates =
						db.getTransportUpdates(txn, c, maxLatency);
				db.commitTransaction(txn);
				return updates;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public Collection<Group> getAvailableGroups() throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Collection<Group> groups = db.getAvailableGroups(txn);
				db.commitTransaction(txn);
				return groups;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public TransportConfig getConfig(TransportId t) throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsTransport(txn, t))
					throw new NoSuchTransportException();
				TransportConfig config = db.getConfig(txn, t);
				db.commitTransaction(txn);
				return config;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public Contact getContact(ContactId c) throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				Contact contact = db.getContact(txn, c);
				db.commitTransaction(txn);
				return contact;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public Collection<Contact> getContacts() throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Collection<Contact> contacts = db.getContacts(txn);
				db.commitTransaction(txn);
				return contacts;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public Group getGroup(GroupId g) throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsGroup(txn, g))
					throw new NoSuchSubscriptionException();
				Group group = db.getGroup(txn, g);
				db.commitTransaction(txn);
				return group;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public Collection<Group> getGroups() throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Collection<Group> groups = db.getGroups(txn);
				db.commitTransaction(txn);
				return groups;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public GroupId getInboxGroupId(ContactId c) throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				GroupId inbox = db.getInboxGroupId(txn, c);
				db.commitTransaction(txn);
				return inbox;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public Collection<MessageHeader> getInboxMessageHeaders(ContactId c)
			throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				Collection<MessageHeader> headers =
						db.getInboxMessageHeaders(txn, c);
				db.commitTransaction(txn);
				return headers;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public LocalAuthor getLocalAuthor(AuthorId a) throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsLocalAuthor(txn, a))
					throw new NoSuchLocalAuthorException();
				LocalAuthor localAuthor = db.getLocalAuthor(txn, a);
				db.commitTransaction(txn);
				return localAuthor;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public Collection<LocalAuthor> getLocalAuthors() throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Collection<LocalAuthor> authors = db.getLocalAuthors(txn);
				db.commitTransaction(txn);
				return authors;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public Map<TransportId, TransportProperties> getLocalProperties()
			throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Map<TransportId, TransportProperties> properties =
						db.getLocalProperties(txn);
				db.commitTransaction(txn);
				return properties;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public TransportProperties getLocalProperties(TransportId t)
			throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsTransport(txn, t))
					throw new NoSuchTransportException();
				TransportProperties properties = db.getLocalProperties(txn, t);
				db.commitTransaction(txn);
				return properties;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public byte[] getMessageBody(MessageId m) throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsMessage(txn, m))
					throw new NoSuchMessageException();
				byte[] body = db.getMessageBody(txn, m);
				db.commitTransaction(txn);
				return body;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public Collection<MessageHeader> getMessageHeaders(GroupId g)
			throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsGroup(txn, g))
					throw new NoSuchSubscriptionException();
				Collection<MessageHeader> headers =
						db.getMessageHeaders(txn, g);
				db.commitTransaction(txn);
				return headers;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public boolean getReadFlag(MessageId m) throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsMessage(txn, m))
					throw new NoSuchMessageException();
				boolean read = db.getReadFlag(txn, m);
				db.commitTransaction(txn);
				return read;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public Map<ContactId, TransportProperties> getRemoteProperties(
			TransportId t) throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Map<ContactId, TransportProperties> properties =
						db.getRemoteProperties(txn, t);
				db.commitTransaction(txn);
				return properties;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public Collection<TemporarySecret> getSecrets() throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Collection<TemporarySecret> secrets = db.getSecrets(txn);
				db.commitTransaction(txn);
				return secrets;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public Settings getSettings() throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Settings s = db.getSettings(txn);
				db.commitTransaction(txn);
				return s;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public Collection<Contact> getSubscribers(GroupId g) throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Collection<Contact> contacts = db.getSubscribers(txn, g);
				db.commitTransaction(txn);
				return contacts;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public Map<TransportId, Integer> getTransportLatencies()
			throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Map<TransportId, Integer> latencies =
						db.getTransportLatencies(txn);
				db.commitTransaction(txn);
				return latencies;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public Map<GroupId, Integer> getUnreadMessageCounts() throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Map<GroupId, Integer> counts = db.getUnreadMessageCounts(txn);
				db.commitTransaction(txn);
				return counts;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public Collection<ContactId> getVisibility(GroupId g) throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsGroup(txn, g))
					throw new NoSuchSubscriptionException();
				Collection<ContactId> visible = db.getVisibility(txn, g);
				db.commitTransaction(txn);
				return visible;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public long incrementStreamCounter(ContactId c, TransportId t,
			long period) throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				if (!db.containsTransport(txn, t))
					throw new NoSuchTransportException();
				long counter = db.incrementStreamCounter(txn, c, t, period);
				db.commitTransaction(txn);
				return counter;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void mergeConfig(TransportId t, TransportConfig c)
			throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsTransport(txn, t))
					throw new NoSuchTransportException();
				db.mergeConfig(txn, t, c);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void mergeLocalProperties(TransportId t, TransportProperties p)
			throws DbException {
		boolean changed = false;
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsTransport(txn, t))
					throw new NoSuchTransportException();
				if (!p.equals(db.getLocalProperties(txn, t))) {
					db.mergeLocalProperties(txn, t, p);
					changed = true;
				}
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		if (changed) eventBus.broadcast(new LocalTransportsUpdatedEvent());
	}

	public void mergeSettings(Settings s) throws DbException {
		boolean changed = false;
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!s.equals(db.getSettings(txn))) {
					db.mergeSettings(txn, s);
					changed = true;
				}
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		if (changed) eventBus.broadcast(new SettingsUpdatedEvent());
	}

	public void receiveAck(ContactId c, Ack a) throws DbException {
		Collection<MessageId> acked = new ArrayList<MessageId>();
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				for (MessageId m : a.getMessageIds()) {
					if (db.containsVisibleMessage(txn, c, m)) {
						db.raiseSeenFlag(txn, c, m);
						acked.add(m);
					}
				}
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		eventBus.broadcast(new MessagesAckedEvent(c, acked));
	}

	public void receiveMessage(ContactId c, Message m) throws DbException {
		boolean duplicate, visible;
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				duplicate = db.containsMessage(txn, m.getId());
				visible = db.containsVisibleGroup(txn, c, m.getGroup().getId());
				if (visible) {
					if (!duplicate) addMessage(txn, m, c);
					db.raiseAckFlag(txn, c, m.getId());
				}
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		if (visible) {
			if (!duplicate)
				eventBus.broadcast(new MessageAddedEvent(m.getGroup(), c));
			eventBus.broadcast(new MessageToAckEvent(c));
		}
	}

	public void receiveOffer(ContactId c, Offer o) throws DbException {
		boolean ack = false, request = false;
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				int count = db.countOfferedMessages(txn, c);
				for (MessageId m : o.getMessageIds()) {
					if (db.containsVisibleMessage(txn, c, m)) {
						db.raiseSeenFlag(txn, c, m);
						db.raiseAckFlag(txn, c, m);
						ack = true;
					} else if (count < MAX_OFFERED_MESSAGES) {
						db.addOfferedMessage(txn, c, m);
						request = true;
						count++;
					}
				}
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		if (ack) eventBus.broadcast(new MessageToAckEvent(c));
		if (request) eventBus.broadcast(new MessageToRequestEvent(c));
	}

	public void receiveRequest(ContactId c, Request r) throws DbException {
		boolean requested = false;
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				for (MessageId m : r.getMessageIds()) {
					if (db.containsVisibleMessage(txn, c, m)) {
						db.raiseRequestedFlag(txn, c, m);
						db.resetExpiryTime(txn, c, m);
						requested = true;
					}
				}
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		if (requested) eventBus.broadcast(new MessageRequestedEvent(c));
	}

	public void receiveRetentionAck(ContactId c, RetentionAck a)
			throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				db.setRetentionUpdateAcked(txn, c, a.getVersion());
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void receiveRetentionUpdate(ContactId c, RetentionUpdate u)
			throws DbException {
		boolean updated;
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				long retention = u.getRetentionTime(), version = u.getVersion();
				updated = db.setRetentionTime(txn, c, retention, version);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		if (updated) eventBus.broadcast(new RemoteRetentionTimeUpdatedEvent(c));
	}

	public void receiveSubscriptionAck(ContactId c, SubscriptionAck a)
			throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				db.setSubscriptionUpdateAcked(txn, c, a.getVersion());
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void receiveSubscriptionUpdate(ContactId c, SubscriptionUpdate u)
			throws DbException {
		boolean updated;
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				updated = db.setGroups(txn, c, u.getGroups(), u.getVersion());
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		if (updated) eventBus.broadcast(new RemoteSubscriptionsUpdatedEvent(c));
	}

	public void receiveTransportAck(ContactId c, TransportAck a)
			throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				if (!db.containsTransport(txn, a.getId()))
					throw new NoSuchTransportException();
				db.setTransportUpdateAcked(txn, c, a.getId(), a.getVersion());
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void receiveTransportUpdate(ContactId c, TransportUpdate u)
			throws DbException {
		boolean updated;
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				TransportId t = u.getId();
				TransportProperties p = u.getProperties();
				long version = u.getVersion();
				updated = db.setRemoteProperties(txn, c, t, p, version);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		if (updated)
			eventBus.broadcast(new RemoteTransportsUpdatedEvent(c, u.getId()));
	}

	public void removeContact(ContactId c) throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				GroupId g = db.getInboxGroupId(txn, c);
				if (g != null) db.removeGroup(txn, g);
				db.removeContact(txn, c);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		eventBus.broadcast(new ContactRemovedEvent(c));
	}

	public void removeGroup(Group g) throws DbException {
		Collection<ContactId> affected;
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				GroupId id = g.getId();
				if (!db.containsGroup(txn, id))
					throw new NoSuchSubscriptionException();
				affected = db.getVisibility(txn, id);
				db.removeGroup(txn, id);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		eventBus.broadcast(new SubscriptionRemovedEvent(g));
		eventBus.broadcast(new LocalSubscriptionsUpdatedEvent(affected));
	}

	public void removeLocalAuthor(AuthorId a) throws DbException {
		Collection<ContactId> affected;
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsLocalAuthor(txn, a))
					throw new NoSuchLocalAuthorException();
				affected = db.getContacts(txn, a);
				for (ContactId c : affected) {
					GroupId g = db.getInboxGroupId(txn, c);
					if (g != null) db.removeGroup(txn, g);
				}
				db.removeLocalAuthor(txn, a);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		for (ContactId c : affected)
			eventBus.broadcast(new ContactRemovedEvent(c));
		eventBus.broadcast(new LocalAuthorRemovedEvent(a));
	}

	public void removeTransport(TransportId t) throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsTransport(txn, t))
					throw new NoSuchTransportException();
				db.removeTransport(txn, t);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		eventBus.broadcast(new TransportRemovedEvent(t));
	}

	public void setReorderingWindow(ContactId c, TransportId t, long period,
			long centre, byte[] bitmap) throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				if (!db.containsTransport(txn, t))
					throw new NoSuchTransportException();
				db.setReorderingWindow(txn, c, t, period, centre, bitmap);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void setInboxGroup(ContactId c, Group g) throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				db.setInboxGroup(txn, c, g);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void setReadFlag(MessageId m, boolean read) throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsMessage(txn, m))
					throw new NoSuchMessageException();
				db.setReadFlag(txn, m, read);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void setRemoteProperties(ContactId c,
			Map<TransportId, TransportProperties> p) throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				db.setRemoteProperties(txn, c, p);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void setVisibility(GroupId g, Collection<ContactId> visible)
			throws DbException {
		Collection<ContactId> affected = new ArrayList<ContactId>();
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsGroup(txn, g))
					throw new NoSuchSubscriptionException();
				// Use HashSets for O(1) lookups, O(n) overall running time
				HashSet<ContactId> now = new HashSet<ContactId>(visible);
				Collection<ContactId> before = db.getVisibility(txn, g);
				before = new HashSet<ContactId>(before);
				// Set the group's visibility for each current contact
				for (ContactId c : db.getContactIds(txn)) {
					boolean wasBefore = before.contains(c);
					boolean isNow = now.contains(c);
					if (!wasBefore && isNow) {
						db.addVisibility(txn, c, g);
						affected.add(c);
					} else if (wasBefore && !isNow) {
						db.removeVisibility(txn, c, g);
						affected.add(c);
					}
				}
				// Make the group invisible to future contacts
				db.setVisibleToAll(txn, g, false);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		if (!affected.isEmpty())
			eventBus.broadcast(new LocalSubscriptionsUpdatedEvent(affected));
	}

	public void setVisibleToAll(GroupId g, boolean all) throws DbException {
		Collection<ContactId> affected = new ArrayList<ContactId>();
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsGroup(txn, g))
					throw new NoSuchSubscriptionException();
				// Make the group visible or invisible to future contacts
				db.setVisibleToAll(txn, g, all);
				if (all) {
					// Make the group visible to all current contacts
					Collection<ContactId> before = db.getVisibility(txn, g);
					before = new HashSet<ContactId>(before);
					for (ContactId c : db.getContactIds(txn)) {
						if (!before.contains(c)) {
							db.addVisibility(txn, c, g);
							affected.add(c);
						}
					}
				}
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		if (!affected.isEmpty())
			eventBus.broadcast(new LocalSubscriptionsUpdatedEvent(affected));
	}

	public void checkFreeSpaceAndClean() throws DbException {
		long freeSpace = db.getFreeSpace();
		if (LOG.isLoggable(INFO)) LOG.info(freeSpace + " bytes free space");
		while (freeSpace < MIN_FREE_SPACE) {
			boolean expired = expireMessages(BYTES_PER_SWEEP);
			if (freeSpace < CRITICAL_FREE_SPACE && !expired) {
				// FIXME: Work out what to do here
				throw new Error("Disk space is critically low");
			}
			Thread.yield();
			freeSpace = db.getFreeSpace();
		}
	}

	/**
	 * Removes the oldest messages from the database, with a total size less
	 * than or equal to the given size, and returns true if any messages were
	 * removed.
	 */
	private boolean expireMessages(int size) throws DbException {
		Collection<MessageId> expired;
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				expired = db.getOldMessages(txn, size);
				if (!expired.isEmpty()) {
					for (MessageId m : expired) db.removeMessage(txn, m);
					db.incrementRetentionVersions(txn);
					if (LOG.isLoggable(INFO))
						LOG.info("Expired " + expired.size() + " messages");
				}
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		if (expired.isEmpty()) return false;
		eventBus.broadcast(new MessageExpiredEvent());
		return true;
	}

	public boolean shouldCheckFreeSpace() {
		if (db.getTransactionCount() > MAX_TRANSACTIONS_BETWEEN_SPACE_CHECKS) {
			db.resetTransactionCount();
			return true;
		}
		return false;
	}
}
