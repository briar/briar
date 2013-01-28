package net.sf.briar.db;

import static java.util.logging.Level.WARNING;
import static net.sf.briar.db.DatabaseConstants.BYTES_PER_SWEEP;
import static net.sf.briar.db.DatabaseConstants.CRITICAL_FREE_SPACE;
import static net.sf.briar.db.DatabaseConstants.MAX_BYTES_BETWEEN_SPACE_CHECKS;
import static net.sf.briar.db.DatabaseConstants.MAX_MS_BETWEEN_SPACE_CHECKS;
import static net.sf.briar.db.DatabaseConstants.MIN_FREE_SPACE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.Rating;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.MessageHeader;
import net.sf.briar.api.db.NoSuchContactException;
import net.sf.briar.api.db.NoSuchContactTransportException;
import net.sf.briar.api.db.event.ContactAddedEvent;
import net.sf.briar.api.db.event.ContactRemovedEvent;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.LocalSubscriptionsUpdatedEvent;
import net.sf.briar.api.db.event.LocalTransportsUpdatedEvent;
import net.sf.briar.api.db.event.MessageAddedEvent;
import net.sf.briar.api.db.event.MessageReceivedEvent;
import net.sf.briar.api.db.event.RatingChangedEvent;
import net.sf.briar.api.db.event.RemoteSubscriptionsUpdatedEvent;
import net.sf.briar.api.db.event.RemoteTransportsUpdatedEvent;
import net.sf.briar.api.db.event.TransportAddedEvent;
import net.sf.briar.api.db.event.TransportRemovedEvent;
import net.sf.briar.api.lifecycle.ShutdownManager;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.ExpiryAck;
import net.sf.briar.api.protocol.ExpiryUpdate;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionAck;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportAck;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.transport.ContactTransport;
import net.sf.briar.api.transport.TemporarySecret;

import com.google.inject.Inject;

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

	/*
	 * Locks must always be acquired in alphabetical order. See the Database
	 * interface to find out which calls require which locks.
	 */

	private final ReentrantReadWriteLock contactLock =
			new ReentrantReadWriteLock(true);
	private final ReentrantReadWriteLock expiryLock =
			new ReentrantReadWriteLock(true);
	private final ReentrantReadWriteLock messageLock =
			new ReentrantReadWriteLock(true);
	private final ReentrantReadWriteLock ratingLock =
			new ReentrantReadWriteLock(true);
	private final ReentrantReadWriteLock subscriptionLock =
			new ReentrantReadWriteLock(true);
	private final ReentrantReadWriteLock transportLock =
			new ReentrantReadWriteLock(true);
	private final ReentrantReadWriteLock windowLock =
			new ReentrantReadWriteLock(true);

	private final Database<T> db;
	private final DatabaseCleaner cleaner;
	private final ShutdownManager shutdown;
	private final Clock clock;

	private final Collection<DatabaseListener> listeners =
			new CopyOnWriteArrayList<DatabaseListener>();

	private final Object spaceLock = new Object();
	private long bytesStoredSinceLastCheck = 0L; // Locking: spaceLock
	private long timeOfLastCheck = 0L; // Locking: spaceLock

	private final Object openCloseLock = new Object();
	private boolean open = false; // Locking: openCloseLock;
	private int shutdownHandle = -1; // Locking: openCloseLock;

	@Inject
	DatabaseComponentImpl(Database<T> db, DatabaseCleaner cleaner,
			ShutdownManager shutdown, Clock clock) {
		this.db = db;
		this.cleaner = cleaner;
		this.shutdown = shutdown;
		this.clock = clock;
	}

	public void open(boolean resume) throws DbException, IOException {
		synchronized(openCloseLock) {
			if(open) throw new IllegalStateException();
			open = true;
			db.open(resume);
			cleaner.startCleaning(this, MAX_MS_BETWEEN_SPACE_CHECKS);
			shutdownHandle = shutdown.addShutdownHook(new Runnable() {
				public void run() {
					try {
						synchronized(openCloseLock) {
							shutdownHandle = -1;
							close();
						}
					} catch(DbException e) {
						if(LOG.isLoggable(WARNING))
							LOG.log(WARNING, e.toString(), e);
					} catch(IOException e) {
						if(LOG.isLoggable(WARNING))
							LOG.log(WARNING, e.toString(), e);
					}
				}
			});
		}
	}

	public void close() throws DbException, IOException {
		synchronized(openCloseLock) {
			if(!open) return;
			open = false;
			if(shutdownHandle != -1)
				shutdown.removeShutdownHook(shutdownHandle);
			cleaner.stopCleaning();
			db.close();
		}
	}

	public void addListener(DatabaseListener d) {
		listeners.add(d);
	}

	public void removeListener(DatabaseListener d) {
		listeners.remove(d);
	}

	public ContactId addContact() throws DbException {
		ContactId c;
		contactLock.writeLock().lock();
		try {
			subscriptionLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					c = db.addContact(txn);
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				subscriptionLock.writeLock().unlock();
			}
		} finally {
			contactLock.writeLock().unlock();
		}
		// Call the listeners outside the lock
		callListeners(new ContactAddedEvent(c));
		return c;
	}

	/** Notifies all listeners of a database event. */
	private void callListeners(DatabaseEvent e) {
		for(DatabaseListener d : listeners) d.eventOccurred(e);
	}

	public void addContactTransport(ContactTransport ct) throws DbException {
		contactLock.readLock().lock();
		try {
			transportLock.readLock().lock();
			try {
				windowLock.writeLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						if(!db.containsContact(txn, ct.getContactId()))
							throw new NoSuchContactException();
						db.addContactTransport(txn, ct);
						db.commitTransaction(txn);
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					}
				} finally {
					windowLock.writeLock().unlock();
				}
			} finally {
				transportLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public void addLocalGroupMessage(Message m) throws DbException {
		boolean added = false;
		contactLock.readLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				subscriptionLock.readLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						// Don't store the message if the user has
						// unsubscribed from the group
						if(db.containsSubscription(txn, m.getGroup()))
							added = storeGroupMessage(txn, m, null);
						db.commitTransaction(txn);
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					}
				} finally {
					subscriptionLock.readLock().unlock();
				}
			} finally {
				messageLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		// Call the listeners outside the lock
		if(added) callListeners(new MessageAddedEvent());
	}

	/**
	 * If the given message is already in the database, marks it as seen by the
	 * sender and returns false. Otherwise stores the message, updates the
	 * sendability of its ancestors if necessary, marks the message as seen by
	 * the sender and unseen by all other contacts, and returns true.
	 * <p>
	 * Locking: contact read, message write.
	 * @param sender may be null for a locally generated message.
	 */
	private boolean storeGroupMessage(T txn, Message m, ContactId sender)
			throws DbException {
		if(m.getGroup() == null) throw new IllegalArgumentException();
		boolean stored = db.addGroupMessage(txn, m);
		// Mark the message as seen by the sender
		MessageId id = m.getId();
		if(sender != null) db.setStatus(txn, sender, id, Status.SEEN);
		if(stored) {
			// Mark the message as unseen by other contacts
			for(ContactId c : db.getContacts(txn)) {
				if(!c.equals(sender)) db.setStatus(txn, c, id, Status.NEW);
			}
			// Calculate and store the message's sendability
			int sendability = calculateSendability(txn, m);
			db.setSendability(txn, id, sendability);
			if(sendability > 0) updateAncestorSendability(txn, id, true);
			// Count the bytes stored
			synchronized(spaceLock) {
				bytesStoredSinceLastCheck += m.getSerialised().length;
			}
		}
		return stored;
	}

	/**
	 * Calculates and returns the sendability score of a message.
	 * <p>
	 * Locking: message write.
	 */
	private int calculateSendability(T txn, Message m) throws DbException {
		int sendability = 0;
		// One point for a good rating
		AuthorId a = m.getAuthor();
		if(a != null && db.getRating(txn, a) == Rating.GOOD) sendability++;
		// One point per sendable child (backward inclusion)
		sendability += db.getNumberOfSendableChildren(txn, m.getId());
		return sendability;
	}


	/**
	 * Iteratively updates the sendability of a message's ancestors to reflect
	 * a change in the message's sendability. Returns the number of ancestors
	 * that have changed from sendable to not sendable, or vice versa.
	 * <p>
	 * Locking: message write.
	 * @param increment true if the message's sendability has changed from 0 to
	 * greater than 0, or false if it has changed from greater than 0 to 0.
	 */
	private int updateAncestorSendability(T txn, MessageId m, boolean increment)
			throws DbException {
		int affected = 0;
		boolean changed = true;
		while(changed) {
			// Stop if the message has no parent, or the parent isn't in the
			// database, or the parent belongs to a different group
			MessageId parent = db.getGroupMessageParent(txn, m);
			if(parent == null) break;
			// Increment or decrement the parent's sendability
			int parentSendability = db.getSendability(txn, parent);
			if(increment) {
				parentSendability++;
				changed = parentSendability == 1;
				if(changed) affected++;
			} else {
				assert parentSendability > 0;
				parentSendability--;
				changed = parentSendability == 0;
				if(changed) affected++;
			}
			db.setSendability(txn, parent, parentSendability);
			// Move on to the parent's parent
			m = parent;
		}
		return affected;
	}

	public void addLocalPrivateMessage(Message m, ContactId c)
			throws DbException {
		boolean added = false;
		contactLock.readLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					added = storePrivateMessage(txn, m, c, false);
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				messageLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		// Call the listeners outside the lock
		if(added) callListeners(new MessageAddedEvent());
	}

	public void addSecrets(Collection<TemporarySecret> secrets)
			throws DbException {
		contactLock.readLock().lock();
		try {
			transportLock.readLock().lock();
			try {
				windowLock.writeLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						Collection<TemporarySecret> relevant =
								new ArrayList<TemporarySecret>();
						for(TemporarySecret s : secrets) {
							ContactId c = s.getContactId();
							TransportId t = s.getTransportId();
							if(db.containsContactTransport(txn, c, t))
								relevant.add(s);
						}
						if(!secrets.isEmpty()) db.addSecrets(txn, relevant);
						db.commitTransaction(txn);
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					}
				} finally {
					windowLock.writeLock().unlock();
				}
			} finally {
				transportLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public void addTransport(TransportId t) throws DbException {
		transportLock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				db.addTransport(txn, t);
				db.commitTransaction(txn);
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			transportLock.writeLock().unlock();
		}
		// Call the listeners outside the lock
		callListeners(new TransportAddedEvent(t));
	}

	/**
	 * Otherwise stores the message and marks it as new or seen with respect to
	 * the given contact, depending on whether the message is outgoing or
	 * incoming, respectively; or returns false if the message is already in
	 * the database.
	 * <p>
	 * Locking: contact read, message write.
	 */
	private boolean storePrivateMessage(T txn, Message m, ContactId c,
			boolean incoming) throws DbException {
		if(m.getGroup() != null) throw new IllegalArgumentException();
		if(m.getAuthor() != null) throw new IllegalArgumentException();
		if(!db.addPrivateMessage(txn, m, c)) return false;
		MessageId id = m.getId();
		if(incoming) db.setStatus(txn, c, id, Status.SEEN);
		else db.setStatus(txn, c, id, Status.NEW);
		// Count the bytes stored
		synchronized(spaceLock) {
			bytesStoredSinceLastCheck += m.getSerialised().length;
		}
		return true;
	}

	public Ack generateAck(ContactId c, int maxMessages) throws DbException {
		Collection<MessageId> acked;
		contactLock.readLock().lock();
		try {
			messageLock.readLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					acked = db.getMessagesToAck(txn, c, maxMessages);
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				messageLock.readLock().unlock();
			}
			if(acked.isEmpty()) return null;
			// Record the contents of the ack
			messageLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					db.removeMessagesToAck(txn, c, acked);
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				messageLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		return new Ack(acked);
	}

	public Collection<byte[]> generateBatch(ContactId c, int maxLength)
			throws DbException {
		Collection<MessageId> ids;
		List<byte[]> messages = new ArrayList<byte[]>();
		// Get some sendable messages from the database
		contactLock.readLock().lock();
		try {
			messageLock.readLock().lock();
			try {
				subscriptionLock.readLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						if(!db.containsContact(txn, c))
							throw new NoSuchContactException();
						ids = db.getSendableMessages(txn, c, maxLength);
						for(MessageId m : ids) {
							messages.add(db.getMessage(txn, m));
						}
						db.commitTransaction(txn);
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					}
				} finally {
					subscriptionLock.readLock().unlock();
				}
			} finally {
				messageLock.readLock().lock();
			}
			if(messages.isEmpty()) return null;
			// Record the message as sent
			messageLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					db.addOutstandingMessages(txn, c, ids);
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				messageLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		return Collections.unmodifiableList(messages);
	}

	public Collection<byte[]> generateBatch(ContactId c, int maxLength,
			Collection<MessageId> requested) throws DbException {
		Collection<MessageId> ids = new ArrayList<MessageId>();
		List<byte[]> messages = new ArrayList<byte[]>();
		// Get some sendable messages from the database
		contactLock.readLock().lock();
		try {
			messageLock.readLock().lock();
			try {
				subscriptionLock.readLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						if(!db.containsContact(txn, c))
							throw new NoSuchContactException();
						Iterator<MessageId> it = requested.iterator();
						while(it.hasNext()) {
							MessageId m = it.next();
							byte[] raw = db.getMessageIfSendable(txn, c, m);
							if(raw != null) {
								if(raw.length > maxLength) break;
								messages.add(raw);
								ids.add(m);
								maxLength -= raw.length;
							}
							it.remove();
						}
						db.commitTransaction(txn);
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					}
				} finally {
					subscriptionLock.readLock().unlock();
				}
			} finally {
				messageLock.readLock().unlock();
			}
			if(messages.isEmpty()) return null;
			// Record the messages as sent
			messageLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					db.addOutstandingMessages(txn, c, ids);
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				messageLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		return Collections.unmodifiableList(messages);
	}

	public ExpiryAck generateExpiryAck(ContactId c) throws DbException {
		contactLock.readLock().lock();
		try {
			expiryLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					ExpiryAck a = db.getExpiryAck(txn, c);
					db.commitTransaction(txn);
					return a;
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				expiryLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public ExpiryUpdate generateExpiryUpdate(ContactId c) throws DbException {
		contactLock.readLock().lock();
		try {
			expiryLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					ExpiryUpdate e = db.getExpiryUpdate(txn, c);
					db.commitTransaction(txn);
					return e;
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				expiryLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public Offer generateOffer(ContactId c, int maxMessages)
			throws DbException {
		Collection<MessageId> offered;
		contactLock.readLock().lock();
		try {
			messageLock.readLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					offered = db.getMessagesToOffer(txn, c, maxMessages);
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				messageLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		return new Offer(offered);
	}

	public SubscriptionAck generateSubscriptionAck(ContactId c)
			throws DbException {
		contactLock.readLock().lock();
		try {
			subscriptionLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					SubscriptionAck a = db.getSubscriptionAck(txn, c);
					db.commitTransaction(txn);
					return a;
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				subscriptionLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public SubscriptionUpdate generateSubscriptionUpdate(ContactId c)
			throws DbException {
		contactLock.readLock().lock();
		try {
			subscriptionLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					SubscriptionUpdate u = db.getSubscriptionUpdate(txn, c);
					db.commitTransaction(txn);
					return u;
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				subscriptionLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public Collection<TransportAck> generateTransportAcks(ContactId c)
			throws DbException {
		contactLock.readLock().lock();
		try {
			transportLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					Collection<TransportAck> acks = db.getTransportAcks(txn, c);
					db.commitTransaction(txn);
					return acks;
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				transportLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public Collection<TransportUpdate> generateTransportUpdates(ContactId c)
			throws DbException {
		contactLock.readLock().lock();
		try {
			transportLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					Collection<TransportUpdate> updates =
							db.getTransportUpdates(txn, c);
					db.commitTransaction(txn);
					return updates;
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				transportLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public TransportConfig getConfig(TransportId t) throws DbException {
		transportLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				TransportConfig config = db.getConfig(txn, t);
				db.commitTransaction(txn);
				return config;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			transportLock.readLock().unlock();
		}
	}

	public Collection<ContactId> getContacts() throws DbException {
		contactLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Collection<ContactId> contacts = db.getContacts(txn);
				db.commitTransaction(txn);
				return contacts;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public TransportProperties getLocalProperties(TransportId t)
			throws DbException {
		transportLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				TransportProperties properties = db.getLocalProperties(txn, t);
				db.commitTransaction(txn);
				return properties;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			transportLock.readLock().unlock();
		}
	}

	public Collection<MessageHeader> getMessageHeaders(GroupId g)
			throws DbException {
		messageLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Collection<MessageHeader> headers =
						db.getMessageHeaders(txn, g);
				db.commitTransaction(txn);
				return headers;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			messageLock.readLock().unlock();
		}
	}

	public Rating getRating(AuthorId a) throws DbException {
		ratingLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Rating r = db.getRating(txn, a);
				db.commitTransaction(txn);
				return r;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			ratingLock.readLock().unlock();
		}
	}

	public Map<ContactId, TransportProperties> getRemoteProperties(
			TransportId t) throws DbException {
		contactLock.readLock().lock();
		try {
			transportLock.readLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					Map<ContactId, TransportProperties> properties =
							db.getRemoteProperties(txn, t);
					db.commitTransaction(txn);
					return properties;
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				transportLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public Collection<TemporarySecret> getSecrets() throws DbException {
		contactLock.readLock().lock();
		try {
			transportLock.readLock().lock();
			try {
				windowLock.readLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						Collection<TemporarySecret> secrets =
								db.getSecrets(txn);
						db.commitTransaction(txn);
						return secrets;
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					}
				} finally {
					windowLock.readLock().unlock();
				}
			} finally {
				transportLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public Collection<Group> getSubscriptions() throws DbException {
		subscriptionLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Collection<Group> subs = db.getSubscriptions(txn);
				db.commitTransaction(txn);
				return subs;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			subscriptionLock.readLock().unlock();
		}
	}

	public Map<GroupId, Integer> getUnreadMessageCounts() throws DbException {
		messageLock.readLock().lock();
		try {
			subscriptionLock.readLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					Map<GroupId, Integer> counts =
							db.getUnreadMessageCounts(txn);
					db.commitTransaction(txn);
					return counts;
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				subscriptionLock.readLock().unlock();
			}
		} finally {
			messageLock.readLock().unlock();
		}
	}

	public Collection<ContactId> getVisibility(GroupId g) throws DbException {
		contactLock.readLock().lock();
		try {
			subscriptionLock.readLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					Collection<ContactId> visible = db.getVisibility(txn, g);
					db.commitTransaction(txn);
					return visible;
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				subscriptionLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public boolean hasSendableMessages(ContactId c) throws DbException {
		contactLock.readLock().lock();
		try {
			messageLock.readLock().lock();
			try {
				subscriptionLock.readLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						if(!db.containsContact(txn, c))
							throw new NoSuchContactException();
						boolean has = db.hasSendableMessages(txn, c);
						db.commitTransaction(txn);
						return has;
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					}
				} finally {
					subscriptionLock.readLock().unlock();
				}
			} finally {
				messageLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public long incrementConnectionCounter(ContactId c, TransportId t,
			long period) throws DbException {
		contactLock.readLock().lock();
		try {
			transportLock.readLock().lock();
			try {
				windowLock.writeLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						if(!db.containsContactTransport(txn, c, t))
							throw new NoSuchContactTransportException();
						long counter = db.incrementConnectionCounter(txn, c, t,
								period);
						db.commitTransaction(txn);
						return counter;
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					}
				} finally {
					windowLock.writeLock().unlock();
				}
			} finally {
				transportLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public void mergeConfig(TransportId t, TransportConfig c)
			throws DbException {
		transportLock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				db.mergeConfig(txn, t, c);
				db.commitTransaction(txn);
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			transportLock.writeLock().unlock();
		}
	}

	public void mergeLocalProperties(TransportId t, TransportProperties p)
			throws DbException {
		boolean changed = false;
		transportLock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if(!p.equals(db.getLocalProperties(txn, t))) {
					db.mergeLocalProperties(txn, t, p);
					changed = true;
				}
				db.commitTransaction(txn);
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			transportLock.writeLock().unlock();
		}
		// Call the listeners outside the lock
		if(changed) callListeners(new LocalTransportsUpdatedEvent());
	}

	public void receiveAck(ContactId c, Ack a) throws DbException {
		contactLock.readLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					db.removeOutstandingMessages(txn, c, a.getMessageIds());
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				messageLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public void receiveExpiryAck(ContactId c, ExpiryAck a) throws DbException {
		contactLock.readLock().lock();
		try {
			expiryLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					db.setExpiryUpdateAcked(txn, c, a.getVersionNumber());
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				expiryLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public void receiveExpiryUpdate(ContactId c, ExpiryUpdate u)
			throws DbException {
		contactLock.readLock().lock();
		try {
			expiryLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					db.setExpiryTime(txn, c, u.getExpiryTime(),
							u.getVersionNumber());
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				expiryLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public void receiveMessage(ContactId c, Message m) throws DbException {
		boolean added = false;
		contactLock.readLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				subscriptionLock.readLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						if(!db.containsContact(txn, c))
							throw new NoSuchContactException();
						added = storeMessage(txn, c, m);
						db.addMessageToAck(txn, c, m.getId());
						db.commitTransaction(txn);
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					}
				} finally {
					subscriptionLock.readLock().unlock();
				}
			} finally {
				messageLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		// Call the listeners outside the lock
		callListeners(new MessageReceivedEvent());
		if(added) callListeners(new MessageAddedEvent());
	}

	/**
	 * Attempts to store a message received from the given contact, and returns
	 * true if it was stored.
	 * <p>
	 * Locking: contact read, message write, subscription read.
	 */
	private boolean storeMessage(T txn, ContactId c, Message m)
			throws DbException {
		GroupId g = m.getGroup();
		if(g == null) return storePrivateMessage(txn, m, c, true);
		if(!db.containsVisibleSubscription(txn, c, g)) return false;
		return storeGroupMessage(txn, m, c);
	}

	public Request receiveOffer(ContactId c, Offer o) throws DbException {
		Collection<MessageId> offered;
		BitSet request;
		contactLock.readLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				subscriptionLock.readLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						if(!db.containsContact(txn, c))
							throw new NoSuchContactException();
						offered = o.getMessageIds();
						request = new BitSet(offered.size());
						Iterator<MessageId> it = offered.iterator();
						for(int i = 0; it.hasNext(); i++) {
							// If the message is not in the database, or not
							// visible to the contact, request it
							MessageId m = it.next();
							if(!db.setStatusSeenIfVisible(txn, c, m))
								request.set(i);
						}
						db.commitTransaction(txn);
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					}
				} finally {
					subscriptionLock.readLock().unlock();
				}
			} finally {
				messageLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		return new Request(request, offered.size());
	}

	public void receiveSubscriptionAck(ContactId c, SubscriptionAck a)
			throws DbException {
		contactLock.readLock().lock();
		try {
			subscriptionLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					db.setSubscriptionUpdateAcked(txn, c, a.getVersionNumber());
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				subscriptionLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public void receiveSubscriptionUpdate(ContactId c, SubscriptionUpdate u)
			throws DbException {
		contactLock.readLock().lock();
		try {
			subscriptionLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					db.setSubscriptions(txn, c, u);
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				subscriptionLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		// Call the listeners outside the lock
		callListeners(new RemoteSubscriptionsUpdatedEvent(c));
	}

	public void receiveTransportAck(ContactId c, TransportAck a)
			throws DbException {
		contactLock.readLock().lock();
		try {
			transportLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					TransportId t = a.getId();
					db.setTransportUpdateAcked(txn, c, t, a.getVersionNumber());
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				transportLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public void receiveTransportUpdate(ContactId c, TransportUpdate u)
			throws DbException {
		contactLock.readLock().lock();
		try {
			transportLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					db.setRemoteProperties(txn, c, u);
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				transportLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		// Call the listeners outside the lock
		callListeners(new RemoteTransportsUpdatedEvent(c, u.getId()));
	}

	public void removeContact(ContactId c) throws DbException {
		contactLock.writeLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				subscriptionLock.writeLock().lock();
				try {
					transportLock.writeLock().lock();
					try {
						windowLock.writeLock().lock();
						try {
							T txn = db.startTransaction();
							try {
								if(!db.containsContact(txn, c))
									throw new NoSuchContactException();
								db.removeContact(txn, c);
								db.commitTransaction(txn);
							} catch(DbException e) {
								db.abortTransaction(txn);
								throw e;
							}
						} finally {
							windowLock.writeLock().unlock();
						}
					} finally {
						transportLock.writeLock().unlock();
					}
				} finally {
					subscriptionLock.writeLock().unlock();
				}
			} finally {
				messageLock.writeLock().unlock();
			}
		} finally {
			contactLock.writeLock().unlock();
		}
		// Call the listeners outside the lock
		callListeners(new ContactRemovedEvent(c));
	}

	public void removeTransport(TransportId t) throws DbException {
		transportLock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				db.removeTransport(txn, t);
				db.commitTransaction(txn);
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			transportLock.writeLock().unlock();
		}
		// Call the listeners outside the lock
		callListeners(new TransportRemovedEvent(t));
	}

	public void setConnectionWindow(ContactId c, TransportId t, long period,
			long centre, byte[] bitmap) throws DbException {
		contactLock.readLock().lock();
		try {
			transportLock.readLock().lock();
			try {
				windowLock.writeLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						if(!db.containsContactTransport(txn, c, t))
							throw new NoSuchContactTransportException();
						db.setConnectionWindow(txn, c, t, period, centre,
								bitmap);
						db.commitTransaction(txn);
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					}
				} finally {
					windowLock.writeLock().unlock();
				}
			} finally {
				transportLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public void setRating(AuthorId a, Rating r) throws DbException {
		boolean changed;
		messageLock.writeLock().lock();
		try {
			ratingLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					Rating old = db.setRating(txn, a, r);
					changed = (old != r);
					// Update the sendability of the author's messages
					if(r == Rating.GOOD && old != Rating.GOOD)
						updateAuthorSendability(txn, a, true);
					else if(r != Rating.GOOD && old == Rating.GOOD)
						updateAuthorSendability(txn, a, false);
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				ratingLock.writeLock().unlock();
			}
		} finally {
			messageLock.writeLock().unlock();
		}
		// Call the listeners outside the lock
		if(changed) callListeners(new RatingChangedEvent(a, r));
	}

	public void setSeen(ContactId c, Collection<MessageId> seen)
			throws DbException {
		contactLock.readLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				subscriptionLock.readLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						if(!db.containsContact(txn, c))
							throw new NoSuchContactException();
						for(MessageId m : seen) {
							db.setStatusSeenIfVisible(txn, c, m);
						}
						db.commitTransaction(txn);
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					}
				} finally {
					subscriptionLock.readLock().unlock();
				}
			} finally {
				messageLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	/**
	 * Updates the sendability of all messages written by the given author, and
	 * the ancestors of those messages if necessary.
	 * <p>
	 * Locking: message write.
	 * @param increment true if the user's rating for the author has changed
	 * from not good to good, or false if it has changed from good to not good.
	 */
	private void updateAuthorSendability(T txn, AuthorId a, boolean increment)
			throws DbException {
		for(MessageId id : db.getMessagesByAuthor(txn, a)) {
			int sendability = db.getSendability(txn, id);
			if(increment) {
				db.setSendability(txn, id, sendability + 1);
				if(sendability == 0)
					updateAncestorSendability(txn, id, true);
			} else {
				assert sendability > 0;
				db.setSendability(txn, id, sendability - 1);
				if(sendability == 1)
					updateAncestorSendability(txn, id, false);
			}
		}
	}

	public void setVisibility(GroupId g, Collection<ContactId> visible)
			throws DbException {
		contactLock.writeLock().lock();
		try {
			subscriptionLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					// Use HashSets for O(1) lookups, O(n) overall running time
					visible = new HashSet<ContactId>(visible);
					HashSet<ContactId> oldVisible =
							new HashSet<ContactId>(db.getVisibility(txn, g));
					// Set the group's visibility for each current contact
					for(ContactId c : db.getContacts(txn)) {
						boolean then = oldVisible.contains(c);
						boolean now = visible.contains(c);
						if(!then && now) db.addVisibility(txn, c, g);
						else if(then && !now) db.removeVisibility(txn, c, g);
					}
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				subscriptionLock.writeLock().unlock();
			}
		} finally {
			contactLock.writeLock().unlock();
		}
	}

	public void subscribe(Group g) throws DbException {
		subscriptionLock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if(!db.containsSubscription(txn, g.getId()))
					db.addSubscription(txn, g);
				db.commitTransaction(txn);
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			subscriptionLock.writeLock().unlock();
		}
		// Listeners will be notified when the group's visibility is set
	}

	public void unsubscribe(GroupId g) throws DbException {
		Collection<ContactId> affected = null;
		contactLock.writeLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				subscriptionLock.writeLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						if(db.containsSubscription(txn, g)) {
							affected = db.getVisibility(txn, g);
							db.removeSubscription(txn, g);
						}
						db.commitTransaction(txn);
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					}
				} finally {
					subscriptionLock.writeLock().unlock();
				}
			} finally {
				messageLock.writeLock().unlock();
			}
		} finally {
			contactLock.writeLock().unlock();
		}
		// Call the listeners outside the lock
		callListeners(new LocalSubscriptionsUpdatedEvent(affected));
	}

	public void checkFreeSpaceAndClean() throws DbException {
		long freeSpace = db.getFreeSpace();
		while(freeSpace < MIN_FREE_SPACE) {
			boolean expired = expireMessages(BYTES_PER_SWEEP);
			if(freeSpace < CRITICAL_FREE_SPACE && !expired) {
				// FIXME: Work out what to do here - the amount of free space
				// is critically low and there are no messages left to expire
				throw new Error("Disk space is critical");
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
		boolean removed = false;
		contactLock.readLock().lock();
		try {
			expiryLock.writeLock().lock();
			try {
				messageLock.writeLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						Collection<MessageId> old =
								db.getOldMessages(txn, size);
						if(!old.isEmpty()) {
							for(MessageId m : old) removeMessage(txn, m);
							db.incrementExpiryVersions(txn);
							removed = true;
						}
						db.commitTransaction(txn);
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					}
				} finally {
					messageLock.writeLock().unlock();
				}
			} finally {
				expiryLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		return removed;
	}

	/**
	 * Removes the given message (and all associated state) from the database.
	 * <p>
	 * Locking: contact read, message write.
	 */
	private void removeMessage(T txn, MessageId m) throws DbException {
		int sendability = db.getSendability(txn, m);
		// If the message is sendable, deleting it may affect its ancestors'
		// sendability (backward inclusion)
		if(sendability > 0) updateAncestorSendability(txn, m, false);
		db.removeMessage(txn, m);
	}

	public boolean shouldCheckFreeSpace() {
		synchronized(spaceLock) {
			long now = clock.currentTimeMillis();
			if(bytesStoredSinceLastCheck > MAX_BYTES_BETWEEN_SPACE_CHECKS
					|| now - timeOfLastCheck > MAX_MS_BETWEEN_SPACE_CHECKS) {
				bytesStoredSinceLastCheck = 0L;
				timeOfLastCheck = now;
				return true;
			}
		}
		return false;
	}

	public void rotateKeys() throws DbException {

	}
}
