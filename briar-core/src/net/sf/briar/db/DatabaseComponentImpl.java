package net.sf.briar.db;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.api.Rating.GOOD;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.Rating;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.GroupMessageHeader;
import net.sf.briar.api.db.NoSuchContactException;
import net.sf.briar.api.db.NoSuchMessageException;
import net.sf.briar.api.db.NoSuchSubscriptionException;
import net.sf.briar.api.db.NoSuchTransportException;
import net.sf.briar.api.db.PrivateMessageHeader;
import net.sf.briar.api.db.event.ContactAddedEvent;
import net.sf.briar.api.db.event.ContactRemovedEvent;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.GroupMessageAddedEvent;
import net.sf.briar.api.db.event.LocalSubscriptionsUpdatedEvent;
import net.sf.briar.api.db.event.LocalTransportsUpdatedEvent;
import net.sf.briar.api.db.event.MessageExpiredEvent;
import net.sf.briar.api.db.event.MessageReceivedEvent;
import net.sf.briar.api.db.event.PrivateMessageAddedEvent;
import net.sf.briar.api.db.event.RatingChangedEvent;
import net.sf.briar.api.db.event.RemoteRetentionTimeUpdatedEvent;
import net.sf.briar.api.db.event.RemoteSubscriptionsUpdatedEvent;
import net.sf.briar.api.db.event.RemoteTransportsUpdatedEvent;
import net.sf.briar.api.db.event.SubscriptionAddedEvent;
import net.sf.briar.api.db.event.SubscriptionRemovedEvent;
import net.sf.briar.api.db.event.TransportAddedEvent;
import net.sf.briar.api.db.event.TransportRemovedEvent;
import net.sf.briar.api.lifecycle.ShutdownManager;
import net.sf.briar.api.messaging.Ack;
import net.sf.briar.api.messaging.Author;
import net.sf.briar.api.messaging.AuthorId;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageId;
import net.sf.briar.api.messaging.Offer;
import net.sf.briar.api.messaging.Request;
import net.sf.briar.api.messaging.RetentionAck;
import net.sf.briar.api.messaging.RetentionUpdate;
import net.sf.briar.api.messaging.SubscriptionAck;
import net.sf.briar.api.messaging.SubscriptionUpdate;
import net.sf.briar.api.messaging.TransportAck;
import net.sf.briar.api.messaging.TransportId;
import net.sf.briar.api.messaging.TransportUpdate;
import net.sf.briar.api.transport.Endpoint;
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
	private static final int MS_BETWEEN_SWEEPS = 10 * 1000; // 10 seconds

	/*
	 * Locks must always be acquired in alphabetical order. See the Database
	 * interface to find out which calls require which locks.
	 */

	private final ReentrantReadWriteLock contactLock =
			new ReentrantReadWriteLock(true);
	private final ReentrantReadWriteLock messageLock =
			new ReentrantReadWriteLock(true);
	private final ReentrantReadWriteLock ratingLock =
			new ReentrantReadWriteLock(true);
	private final ReentrantReadWriteLock retentionLock =
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
	private long bytesStoredSinceLastCheck = 0; // Locking: spaceLock
	private long timeOfLastCheck = 0; // Locking: spaceLock

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
			cleaner.startCleaning(this, MS_BETWEEN_SWEEPS);
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

	public ContactId addContact(String name) throws DbException {
		ContactId c;
		contactLock.writeLock().lock();
		try {
			retentionLock.writeLock().lock();
			try {
				subscriptionLock.writeLock().lock();
				try {
					transportLock.writeLock().lock();
					try {
						windowLock.writeLock().lock();
						try {
							T txn = db.startTransaction();
							try {
								c = db.addContact(txn, name);
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
				retentionLock.writeLock().unlock();
			}
		} finally {
			contactLock.writeLock().unlock();
		}
		callListeners(new ContactAddedEvent(c));
		return c;
	}

	/** Notifies all listeners of a database event. */
	private void callListeners(DatabaseEvent e) {
		for(DatabaseListener d : listeners) d.eventOccurred(e);
	}

	public void addEndpoint(Endpoint ep) throws DbException {
		contactLock.readLock().lock();
		try {
			transportLock.readLock().lock();
			try {
				windowLock.writeLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						if(!db.containsContact(txn, ep.getContactId()))
							throw new NoSuchContactException();
						if(!db.containsTransport(txn, ep.getTransportId()))
							throw new NoSuchTransportException();
						db.addEndpoint(txn, ep);
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
				ratingLock.readLock().lock();
				try {
					subscriptionLock.readLock().lock();
					try {
						T txn = db.startTransaction();
						try {
							// Don't store the message if the user has
							// unsubscribed from the group
							GroupId g = m.getGroup().getId();
							if(db.containsSubscription(txn, g))
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
					ratingLock.readLock().unlock();
				}
			} finally {
				messageLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		if(added) {
			GroupId g = m.getGroup().getId();
			callListeners(new GroupMessageAddedEvent(g, false));
		}
	}

	/**
	 * If the given message is already in the database, marks it as seen by the
	 * sender and returns false. Otherwise stores the message, updates the
	 * sendability of its ancestors if necessary, marks the message as seen by
	 * the sender and unseen by all other contacts, and returns true.
	 * <p>
	 * Locking: contact read, message write, rating read.
	 * @param sender is null for a locally generated message.
	 */
	private boolean storeGroupMessage(T txn, Message m, ContactId sender)
			throws DbException {
		if(m.getGroup() == null) throw new IllegalArgumentException();
		boolean stored = db.addGroupMessage(txn, m);
		if(stored && sender == null) db.setReadFlag(txn, m.getId(), true);
		// Mark the message as seen by the sender
		MessageId id = m.getId();
		if(sender != null) db.addStatus(txn, sender, id, true);
		if(stored) {
			// Mark the message as unseen by other contacts
			for(ContactId c : db.getContactIds(txn)) {
				if(!c.equals(sender)) db.addStatus(txn, c, id, false);
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
	 * Locking: message read, rating read.
	 */
	private int calculateSendability(T txn, Message m) throws DbException {
		int sendability = 0;
		// One point for a good rating
		Author a = m.getAuthor();
		if(a != null && db.getRating(txn, a.getId()) == GOOD) sendability++;
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
		boolean added;
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
		if(added) callListeners(new PrivateMessageAddedEvent(c, false));
	}

	/**
	 * If the given message is already in the database, returns false.
	 * Otherwise stores the message and marks it as new or seen with respect to
	 * the given contact, depending on whether the message is outgoing or
	 * incoming, respectively.
	 * <p>
	 * Locking: message write.
	 */
	private boolean storePrivateMessage(T txn, Message m, ContactId c,
			boolean incoming) throws DbException {
		if(m.getGroup() != null) throw new IllegalArgumentException();
		if(m.getAuthor() != null) throw new IllegalArgumentException();
		if(!db.addPrivateMessage(txn, m, c)) return false;
		if(!incoming) db.setReadFlag(txn, m.getId(), true);
		db.addStatus(txn, c, m.getId(), incoming);
		// Count the bytes stored
		synchronized(spaceLock) {
			bytesStoredSinceLastCheck += m.getSerialised().length;
		}
		return true;
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
							if(!db.containsContact(txn, c)) continue;
							TransportId t = s.getTransportId();
							if(!db.containsTransport(txn, t)) continue;
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

	public boolean addTransport(TransportId t) throws DbException {
		boolean added;
		transportLock.writeLock().lock();
		try {
			windowLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					added = db.addTransport(txn, t);
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
		if(added) callListeners(new TransportAddedEvent(t));
		return added;
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

	public Collection<byte[]> generateBatch(ContactId c, int maxLength,
			long maxLatency) throws DbException {
		Collection<MessageId> ids;
		Map<MessageId, Integer> sent = new HashMap<MessageId, Integer>();
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
							messages.add(db.getRawMessage(txn, m));
							sent.put(m, db.getTransmissionCount(txn, c, m));
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
					db.updateExpiryTimes(txn, c, sent, maxLatency);
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
			long maxLatency, Collection<MessageId> requested)
					throws DbException {
		Map<MessageId, Integer> sent = new HashMap<MessageId, Integer>();
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
							byte[] raw = db.getRawMessageIfSendable(txn, c, m);
							if(raw != null) {
								if(raw.length > maxLength) break;
								messages.add(raw);
								sent.put(m, db.getTransmissionCount(txn, c, m));
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
					db.updateExpiryTimes(txn, c, sent, maxLatency);
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

	public Offer generateOffer(ContactId c, int maxMessages)
			throws DbException {
		Collection<MessageId> offered;
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
						offered = db.getMessagesToOffer(txn, c, maxMessages);
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
		} finally {
			contactLock.readLock().unlock();
		}
		return new Offer(offered);
	}

	public RetentionAck generateRetentionAck(ContactId c) throws DbException {
		contactLock.readLock().lock();
		try {
			retentionLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					RetentionAck a = db.getRetentionAck(txn, c);
					db.commitTransaction(txn);
					return a;
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				retentionLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public RetentionUpdate generateRetentionUpdate(ContactId c, long maxLatency)
			throws DbException {
		contactLock.readLock().lock();
		try {
			messageLock.readLock().lock();
			try {
				retentionLock.writeLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						if(!db.containsContact(txn, c))
							throw new NoSuchContactException();
						RetentionUpdate u =
								db.getRetentionUpdate(txn, c, maxLatency);
						db.commitTransaction(txn);
						return u;
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					}
				} finally {
					retentionLock.writeLock().unlock();
				}
			} finally {
				messageLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
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

	public SubscriptionUpdate generateSubscriptionUpdate(ContactId c,
			long maxLatency) throws DbException {
		contactLock.readLock().lock();
		try {
			subscriptionLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					SubscriptionUpdate u =
							db.getSubscriptionUpdate(txn, c, maxLatency);
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
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
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

	public Collection<TransportUpdate> generateTransportUpdates(ContactId c,
			long maxLatency) throws DbException {
		contactLock.readLock().lock();
		try {
			transportLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					Collection<TransportUpdate> updates =
							db.getTransportUpdates(txn, c, maxLatency);
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
				if(!db.containsTransport(txn, t))
					throw new NoSuchTransportException();
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

	public Contact getContact(ContactId c) throws DbException {
		contactLock.readLock().lock();
		try {
			windowLock.readLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					Contact contact = db.getContact(txn, c);
					db.commitTransaction(txn);
					return contact;
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				windowLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public Collection<Contact> getContacts() throws DbException {
		contactLock.readLock().lock();
		try {
			windowLock.readLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					Collection<Contact> contacts = db.getContacts(txn);
					db.commitTransaction(txn);
					return contacts;
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				windowLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public Group getGroup(GroupId g) throws DbException {
		subscriptionLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if(!db.containsSubscription(txn, g))
					throw new NoSuchSubscriptionException();
				Group group = db.getGroup(txn, g);
				db.commitTransaction(txn);
				return group;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			subscriptionLock.readLock().unlock();
		}
	}

	public TransportProperties getLocalProperties(TransportId t)
			throws DbException {
		transportLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if(!db.containsTransport(txn, t))
					throw new NoSuchTransportException();
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

	public byte[] getMessageBody(MessageId m) throws DbException {
		messageLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if(!db.containsMessage(txn, m))
					throw new NoSuchMessageException();
				byte[] body = db.getMessageBody(txn, m);
				db.commitTransaction(txn);
				return body;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			messageLock.readLock().unlock();
		}
	}

	public Collection<GroupMessageHeader> getMessageHeaders(GroupId g)
			throws DbException {
		messageLock.readLock().lock();
		try {
			ratingLock.readLock().lock();
			try {
				subscriptionLock.readLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						if(!db.containsSubscription(txn, g))
							throw new NoSuchSubscriptionException();
						Collection<GroupMessageHeader> headers =
								db.getMessageHeaders(txn, g);
						db.commitTransaction(txn);
						return headers;
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					}
				} finally {
					subscriptionLock.readLock().unlock();
				}
			} finally {
				ratingLock.readLock().unlock();
			}
		} finally {
			messageLock.readLock().unlock();
		}
	}

	public Collection<PrivateMessageHeader> getPrivateMessageHeaders()
			throws DbException {
		messageLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Collection<PrivateMessageHeader> headers =
						db.getPrivateMessageHeaders(txn);
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

	public Collection<PrivateMessageHeader> getPrivateMessageHeaders(
			ContactId c) throws DbException {
		messageLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Collection<PrivateMessageHeader> headers =
						db.getPrivateMessageHeaders(txn, c);
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

	public boolean getReadFlag(MessageId m) throws DbException {
		messageLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if(!db.containsMessage(txn, m))
					throw new NoSuchMessageException();
				boolean read = db.getReadFlag(txn, m);
				db.commitTransaction(txn);
				return read;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			messageLock.readLock().unlock();
		}
	}

	public Map<ContactId, TransportProperties> getRemoteProperties(
			TransportId t) throws DbException {
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
	}

	public Collection<TemporarySecret> getSecrets() throws DbException {
		windowLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Collection<TemporarySecret> secrets = db.getSecrets(txn);
				db.commitTransaction(txn);
				return secrets;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			windowLock.readLock().unlock();
		}
	}

	public boolean getStarredFlag(MessageId m) throws DbException {
		messageLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if(!db.containsMessage(txn, m))
					throw new NoSuchMessageException();
				boolean starred = db.getStarredFlag(txn, m);
				db.commitTransaction(txn);
				return starred;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			messageLock.readLock().unlock();
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
			T txn = db.startTransaction();
			try {
				Map<GroupId, Integer> counts = db.getUnreadMessageCounts(txn);
				db.commitTransaction(txn);
				return counts;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			messageLock.readLock().unlock();
		}
	}

	public Collection<ContactId> getVisibility(GroupId g) throws DbException {
		subscriptionLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if(!db.containsSubscription(txn, g))
					throw new NoSuchSubscriptionException();
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
	}

	public Collection<GroupId> getVisibleSubscriptions(ContactId c)
			throws DbException {
		contactLock.readLock().lock();
		try {
			subscriptionLock.readLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					Collection<GroupId> visible =
							db.getVisibleSubscriptions(txn, c);
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
						if(!db.containsContact(txn, c))
							throw new NoSuchContactException();
						if(!db.containsTransport(txn, t))
							throw new NoSuchTransportException();
						long counter = db.incrementConnectionCounter(txn, c, t,
								period);
						db.setLastConnected(txn, c, clock.currentTimeMillis());
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
				if(!db.containsTransport(txn, t))
					throw new NoSuchTransportException();
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
				if(!db.containsTransport(txn, t))
					throw new NoSuchTransportException();
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

	public void receiveMessage(ContactId c, Message m) throws DbException {
		boolean added = false;
		contactLock.readLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				ratingLock.readLock().lock();
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
					ratingLock.readLock().unlock();
				}
			} finally {
				messageLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		callListeners(new MessageReceivedEvent(c));
		if(added) {
			Group g = m.getGroup();
			if(g == null) callListeners(new PrivateMessageAddedEvent(c, true));
			else callListeners(new GroupMessageAddedEvent(g.getId(), true));
		}
	}

	/**
	 * Attempts to store a message received from the given contact, and returns
	 * true if it was stored.
	 * <p>
	 * Locking: contact read, message write, rating read, subscription read.
	 */
	private boolean storeMessage(T txn, ContactId c, Message m)
			throws DbException {
		if(m.getTimestamp() > clock.currentTimeMillis()) return false;
		Group g = m.getGroup();
		if(g == null) return storePrivateMessage(txn, m, c, true);
		if(!db.containsVisibleSubscription(txn, c, g.getId())) return false;
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

	public void receiveRetentionAck(ContactId c, RetentionAck a)
			throws DbException {
		contactLock.readLock().lock();
		try {
			retentionLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					db.setRetentionUpdateAcked(txn, c, a.getVersion());
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				retentionLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public void receiveRetentionUpdate(ContactId c, RetentionUpdate u)
			throws DbException {
		contactLock.readLock().lock();
		try {
			retentionLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					db.setRetentionTime(txn, c, u.getRetentionTime(),
							u.getVersion());
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				retentionLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		callListeners(new RemoteRetentionTimeUpdatedEvent(c));
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
					db.setSubscriptionUpdateAcked(txn, c, a.getVersion());
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
					db.setSubscriptions(txn, c, u.getGroups(), u.getVersion());
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
					if(!db.containsTransport(txn, t))
						throw new NoSuchTransportException();
					db.setTransportUpdateAcked(txn, c, t, a.getVersion());
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
					db.setRemoteProperties(txn, c, u.getId(), u.getProperties(),
							u.getVersion());
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
		callListeners(new RemoteTransportsUpdatedEvent(c, u.getId()));
	}

	public void removeContact(ContactId c) throws DbException {
		contactLock.writeLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				retentionLock.writeLock().lock();
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
					retentionLock.writeLock().unlock();
				}
			} finally {
				messageLock.writeLock().unlock();
			}
		} finally {
			contactLock.writeLock().unlock();
		}
		callListeners(new ContactRemovedEvent(c));
	}

	public void removeTransport(TransportId t) throws DbException {
		transportLock.writeLock().lock();
		try {
			windowLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsTransport(txn, t))
						throw new NoSuchTransportException();
					db.removeTransport(txn, t);
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
						if(!db.containsContact(txn, c))
							throw new NoSuchContactException();
						if(!db.containsTransport(txn, t))
							throw new NoSuchTransportException();
						db.setConnectionWindow(txn, c, t, period, centre,
								bitmap);
						db.setLastConnected(txn, c, clock.currentTimeMillis());
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
					if(r == GOOD && old != GOOD)
						updateAuthorSendability(txn, a, true);
					else if(r != GOOD && old == GOOD)
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
		if(changed) callListeners(new RatingChangedEvent(a, r));
	}

	public boolean setReadFlag(MessageId m, boolean read) throws DbException {
		messageLock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if(!db.containsMessage(txn, m))
					throw new NoSuchMessageException();
				boolean wasRead = db.setReadFlag(txn, m, read);
				db.commitTransaction(txn);
				return wasRead;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			messageLock.writeLock().unlock();
		}
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
						for(MessageId m : seen)
							db.setStatusSeenIfVisible(txn, c, m);
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

	public boolean setStarredFlag(MessageId m, boolean starred)
			throws DbException {
		messageLock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if(!db.containsMessage(txn, m))
					throw new NoSuchMessageException();
				boolean wasStarred = db.setStarredFlag(txn, m, starred);
				db.commitTransaction(txn);
				return wasStarred;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			messageLock.writeLock().unlock();
		}
	}

	public void setVisibility(GroupId g, Collection<ContactId> visible)
			throws DbException {
		Collection<ContactId> affected = new ArrayList<ContactId>();
		contactLock.readLock().lock();
		try {
			subscriptionLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsSubscription(txn, g))
						throw new NoSuchSubscriptionException();
					// Use HashSets for O(1) lookups, O(n) overall running time
					HashSet<ContactId> newVisible =
							new HashSet<ContactId>(visible);
					HashSet<ContactId> oldVisible =
							new HashSet<ContactId>(db.getVisibility(txn, g));
					// Set the group's visibility for each current contact
					for(ContactId c : db.getContactIds(txn)) {
						boolean then = oldVisible.contains(c);
						boolean now = newVisible.contains(c);
						if(!then && now) {
							db.addVisibility(txn, c, g);
							affected.add(c);
						} else if(then && !now) {
							db.removeVisibility(txn, c, g);
							affected.add(c);
						}
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
			contactLock.readLock().unlock();
		}
		if(!affected.isEmpty())
			callListeners(new LocalSubscriptionsUpdatedEvent(affected));
	}

	public boolean subscribe(Group g) throws DbException {
		boolean added = false;
		subscriptionLock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if(!db.containsSubscription(txn, g.getId()))
					added = db.addSubscription(txn, g);
				db.commitTransaction(txn);
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			subscriptionLock.writeLock().unlock();
		}
		if(added) callListeners(new SubscriptionAddedEvent(g));
		return added;
	}

	public void unsubscribe(GroupId g) throws DbException {
		Collection<ContactId> affected;
		messageLock.writeLock().lock();
		try {
			subscriptionLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsSubscription(txn, g))
						throw new NoSuchSubscriptionException();
					affected = db.getVisibility(txn, g);
					db.removeSubscription(txn, g);
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
		callListeners(new SubscriptionRemovedEvent(g));
		callListeners(new LocalSubscriptionsUpdatedEvent(affected));
	}

	public void checkFreeSpaceAndClean() throws DbException {
		long freeSpace = db.getFreeSpace();
		if(LOG.isLoggable(INFO)) LOG.info(freeSpace + " bytes free space");
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
		Collection<MessageId> expired;
		messageLock.writeLock().lock();
		try {
			retentionLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					expired = db.getOldMessages(txn, size);
					if(!expired.isEmpty()) {
						for(MessageId m : expired) removeMessage(txn, m);
						db.incrementRetentionVersions(txn);
						if(LOG.isLoggable(INFO))
							LOG.info("Expired " + expired.size() + " messages");
					}
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				retentionLock.writeLock().unlock();
			}
		} finally {
			messageLock.writeLock().unlock();
		}
		if(expired.isEmpty()) return false;
		callListeners(new MessageExpiredEvent(expired));
		return true;
	}

	/**
	 * Removes the given message (and all associated state) from the database.
	 * <p>
	 * Locking: message write.
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
				bytesStoredSinceLastCheck = 0;
				timeOfLastCheck = now;
				return true;
			}
		}
		return false;
	}
}
