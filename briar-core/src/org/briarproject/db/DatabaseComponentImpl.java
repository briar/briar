package org.briarproject.db;

import org.briarproject.api.DeviceId;
import org.briarproject.api.TransportId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.ContactExistsException;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.LocalAuthorExistsException;
import org.briarproject.api.db.MessageExistsException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.NoSuchContactException;
import org.briarproject.api.db.NoSuchLocalAuthorException;
import org.briarproject.api.db.NoSuchMessageException;
import org.briarproject.api.db.NoSuchSubscriptionException;
import org.briarproject.api.db.NoSuchTransportException;
import org.briarproject.api.db.StorageStatus;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.LocalSubscriptionsUpdatedEvent;
import org.briarproject.api.event.MessageAddedEvent;
import org.briarproject.api.event.MessageRequestedEvent;
import org.briarproject.api.event.MessageSharedEvent;
import org.briarproject.api.event.MessageToAckEvent;
import org.briarproject.api.event.MessageToRequestEvent;
import org.briarproject.api.event.MessageValidatedEvent;
import org.briarproject.api.event.MessagesAckedEvent;
import org.briarproject.api.event.MessagesSentEvent;
import org.briarproject.api.event.SettingsUpdatedEvent;
import org.briarproject.api.event.SubscriptionAddedEvent;
import org.briarproject.api.event.SubscriptionRemovedEvent;
import org.briarproject.api.event.TransportAddedEvent;
import org.briarproject.api.event.TransportRemovedEvent;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.lifecycle.ShutdownManager;
import org.briarproject.api.settings.Settings;
import org.briarproject.api.sync.Ack;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.MessageStatus;
import org.briarproject.api.sync.Offer;
import org.briarproject.api.sync.Request;
import org.briarproject.api.sync.ValidationManager.Validity;
import org.briarproject.api.transport.TransportKeys;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static org.briarproject.api.sync.ValidationManager.Validity.UNKNOWN;
import static org.briarproject.api.sync.ValidationManager.Validity.VALID;
import static org.briarproject.db.DatabaseConstants.MAX_OFFERED_MESSAGES;

/**
 * An implementation of DatabaseComponent using reentrant read-write locks.
 * Depending on the JVM's lock implementation, this implementation may allow
 * writers to starve. LockFairnessTest can be used to test whether this
 * implementation is safe on a given JVM.
 */
class DatabaseComponentImpl<T> implements DatabaseComponent {

	private static final Logger LOG =
			Logger.getLogger(DatabaseComponentImpl.class.getName());

	private final Database<T> db;
	private final EventBus eventBus;
	private final ShutdownManager shutdown;

	private final ReentrantReadWriteLock lock =
			new ReentrantReadWriteLock(true);

	private boolean open = false; // Locking: lock.writeLock
	private int shutdownHandle = -1; // Locking: lock.writeLock

	@Inject
	DatabaseComponentImpl(Database<T> db, EventBus eventBus,
			ShutdownManager shutdown) {
		this.db = db;
		this.eventBus = eventBus;
		this.shutdown = shutdown;
	}

	public boolean open() throws DbException {
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
			db.close();
		} finally {
			lock.writeLock().unlock();
		}
	}

	public ContactId addContact(Author remote, AuthorId local)
			throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsLocalAuthor(txn, local))
					throw new NoSuchLocalAuthorException();
				if (db.containsContact(txn, remote.getId(), local))
					throw new ContactExistsException();
				ContactId c = db.addContact(txn, remote, local);
				db.commitTransaction(txn);
				return c;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void addContactGroup(ContactId c, Group g) throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				db.addContactGroup(txn, c, g);
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
	}

	public void addLocalMessage(Message m, ClientId c, Metadata meta,
			boolean shared) throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (db.containsMessage(txn, m.getId()))
					throw new MessageExistsException();
				if (!db.containsGroup(txn, m.getGroupId()))
					throw new NoSuchSubscriptionException();
				addMessage(txn, m, VALID, shared, null);
				db.mergeMessageMetadata(txn, m.getId(), meta);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		eventBus.broadcast(new MessageAddedEvent(m, null));
		eventBus.broadcast(new MessageValidatedEvent(m, c, true, true));
	}

	/**
	 * Stores a message and initialises its status with respect to each contact.
	 * <p>
	 * Locking: write.
	 * @param sender null for a locally generated message.
	 */
	private void addMessage(T txn, Message m, Validity validity, boolean shared,
			ContactId sender) throws DbException {
		db.addMessage(txn, m, validity, shared);
		GroupId g = m.getGroupId();
		Collection<ContactId> visibility = db.getVisibility(txn, g);
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

	public void addTransportKeys(ContactId c, TransportKeys k)
			throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				if (!db.containsTransport(txn, k.getTransportId()))
					throw new NoSuchTransportException();
				db.addTransportKeys(txn, c, k);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
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
		if (!ids.isEmpty()) eventBus.broadcast(new MessagesSentEvent(c, ids));
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
		if (!ids.isEmpty()) eventBus.broadcast(new MessagesSentEvent(c, ids));
		return Collections.unmodifiableList(messages);
	}

	public Collection<Group> getAvailableGroups(ClientId c) throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Collection<Group> groups = db.getAvailableGroups(txn, c);
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

	public Collection<ContactId> getContacts(AuthorId a) throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsLocalAuthor(txn, a))
					throw new NoSuchLocalAuthorException();
				Collection<ContactId> contacts = db.getContacts(txn, a);
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

	public DeviceId getDeviceId() throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				DeviceId id = db.getDeviceId(txn);
				db.commitTransaction(txn);
				return id;
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

	public Metadata getGroupMetadata(GroupId g) throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsGroup(txn, g))
					throw new NoSuchSubscriptionException();
				Metadata metadata = db.getGroupMetadata(txn, g);
				db.commitTransaction(txn);
				return metadata;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public Collection<Group> getGroups(ClientId c) throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Collection<Group> groups = db.getGroups(txn, c);
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

	public Collection<MessageId> getMessagesToValidate(ClientId c)
			throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Collection<MessageId> ids = db.getMessagesToValidate(txn, c);
				db.commitTransaction(txn);
				return ids;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public byte[] getRawMessage(MessageId m) throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsMessage(txn, m))
					throw new NoSuchMessageException();
				byte[] raw = db.getRawMessage(txn, m);
				db.commitTransaction(txn);
				return raw;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public Map<MessageId, Metadata> getMessageMetadata(GroupId g)
			throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsGroup(txn, g))
					throw new NoSuchSubscriptionException();
				Map<MessageId, Metadata> metadata =
						db.getMessageMetadata(txn, g);
				db.commitTransaction(txn);
				return metadata;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public Metadata getMessageMetadata(MessageId m) throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsMessage(txn, m))
					throw new NoSuchMessageException();
				Metadata metadata = db.getMessageMetadata(txn, m);
				db.commitTransaction(txn);
				return metadata;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public Collection<MessageStatus> getMessageStatus(ContactId c, GroupId g)
			throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				if (!db.containsGroup(txn, g))
					throw new NoSuchSubscriptionException();
				Collection<MessageStatus> statuses =
						db.getMessageStatus(txn, c, g);
				db.commitTransaction(txn);
				return statuses;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public MessageStatus getMessageStatus(ContactId c, MessageId m)
			throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				if (!db.containsMessage(txn, m))
					throw new NoSuchMessageException();
				MessageStatus status = db.getMessageStatus(txn, c, m);
				db.commitTransaction(txn);
				return status;
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	public Settings getSettings(String namespace) throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Settings s = db.getSettings(txn, namespace);
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

	public Map<ContactId, TransportKeys> getTransportKeys(TransportId t)
			throws DbException {
		lock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsTransport(txn, t))
					throw new NoSuchTransportException();
				Map<ContactId, TransportKeys> keys =
						db.getTransportKeys(txn, t);
				db.commitTransaction(txn);
				return keys;
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

	public void incrementStreamCounter(ContactId c, TransportId t,
			long rotationPeriod) throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				if (!db.containsTransport(txn, t))
					throw new NoSuchTransportException();
				db.incrementStreamCounter(txn, c, t, rotationPeriod);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void mergeGroupMetadata(GroupId g, Metadata meta)
			throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsGroup(txn, g))
					throw new NoSuchSubscriptionException();
				db.mergeGroupMetadata(txn, g, meta);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void mergeMessageMetadata(MessageId m, Metadata meta)
			throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsMessage(txn, m))
					throw new NoSuchMessageException();
				db.mergeMessageMetadata(txn, m, meta);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void mergeSettings(Settings s, String namespace) throws DbException {
		boolean changed = false;
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Settings old = db.getSettings(txn, namespace);
				Settings merged = new Settings();
				merged.putAll(old);
				merged.putAll(s);
				if (!merged.equals(old)) {
					db.mergeSettings(txn, s, namespace);
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
		if (changed) eventBus.broadcast(new SettingsUpdatedEvent(namespace));
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
				visible = db.containsVisibleGroup(txn, c, m.getGroupId());
				if (visible) {
					if (!duplicate) addMessage(txn, m, UNKNOWN, true, c);
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
				eventBus.broadcast(new MessageAddedEvent(m, c));
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

	public void removeContact(ContactId c) throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				db.removeContact(txn, c);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
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
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsLocalAuthor(txn, a))
					throw new NoSuchLocalAuthorException();
				db.removeLocalAuthor(txn, a);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
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

	public void setContactStatus(ContactId c, StorageStatus s)
			throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				db.setContactStatus(txn, c, s);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void setLocalAuthorStatus(AuthorId a, StorageStatus s)
			throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsLocalAuthor(txn, a))
					throw new NoSuchLocalAuthorException();
				db.setLocalAuthorStatus(txn, a, s);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void setMessageShared(Message m, boolean shared)
			throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsMessage(txn, m.getId()))
					throw new NoSuchMessageException();
				db.setMessageShared(txn, m.getId(), shared);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		if (shared) eventBus.broadcast(new MessageSharedEvent(m));
	}

	public void setMessageValid(Message m, ClientId c, boolean valid)
			throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsMessage(txn, m.getId()))
					throw new NoSuchMessageException();
				db.setMessageValid(txn, m.getId(), valid);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
		eventBus.broadcast(new MessageValidatedEvent(m, c, false, valid));
	}

	public void setReorderingWindow(ContactId c, TransportId t,
			long rotationPeriod, long base, byte[] bitmap) throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if (!db.containsContact(txn, c))
					throw new NoSuchContactException();
				if (!db.containsTransport(txn, t))
					throw new NoSuchTransportException();
				db.setReorderingWindow(txn, c, t, rotationPeriod, base, bitmap);
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

	public void updateTransportKeys(Map<ContactId, TransportKeys> keys)
			throws DbException {
		lock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Map<ContactId, TransportKeys> filtered =
						new HashMap<ContactId, TransportKeys>();
				for (Entry<ContactId, TransportKeys> e : keys.entrySet()) {
					ContactId c = e.getKey();
					TransportKeys k = e.getValue();
					if (db.containsContact(txn, c)
							&& db.containsTransport(txn, k.getTransportId())) {
						filtered.put(c, k);
					}
				}
				db.updateTransportKeys(txn, filtered);
				db.commitTransaction(txn);
			} catch (DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}
}
