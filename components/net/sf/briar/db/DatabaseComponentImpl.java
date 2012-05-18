package net.sf.briar.db;

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
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.Rating;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.MessageHeader;
import net.sf.briar.api.db.NoSuchContactException;
import net.sf.briar.api.db.Status;
import net.sf.briar.api.db.event.BatchReceivedEvent;
import net.sf.briar.api.db.event.ContactAddedEvent;
import net.sf.briar.api.db.event.ContactRemovedEvent;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.LocalTransportsUpdatedEvent;
import net.sf.briar.api.db.event.MessagesAddedEvent;
import net.sf.briar.api.db.event.RatingChangedEvent;
import net.sf.briar.api.db.event.RemoteTransportsUpdatedEvent;
import net.sf.briar.api.db.event.SubscriptionsUpdatedEvent;
import net.sf.briar.api.db.event.TransportAddedEvent;
import net.sf.briar.api.lifecycle.ShutdownManager;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.PacketFactory;
import net.sf.briar.api.protocol.RawBatch;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionWindow;
import net.sf.briar.util.ByteUtils;

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
	private final ReentrantReadWriteLock messageLock =
		new ReentrantReadWriteLock(true);
	private final ReentrantReadWriteLock messageFlagLock =
		new ReentrantReadWriteLock(true);
	private final ReentrantReadWriteLock messageStatusLock =
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
	private final PacketFactory packetFactory;

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
			ShutdownManager shutdown, PacketFactory packetFactory) {
		this.db = db;
		this.cleaner = cleaner;
		this.shutdown = shutdown;
		this.packetFactory = packetFactory;
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
						if(LOG.isLoggable(Level.WARNING))
							LOG.warning(e.toString());
					} catch(IOException e) {
						if(LOG.isLoggable(Level.WARNING))
							LOG.warning(e.toString());
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

	public ContactId addContact(byte[] inSecret, byte[] outSecret)
	throws DbException {
		ContactId c;
		Collection<byte[]> erase = new ArrayList<byte[]>();
		contactLock.writeLock().lock();
		try {
			subscriptionLock.writeLock().lock();
			try {
				transportLock.writeLock().lock();
				try {
					windowLock.writeLock().lock();
					try {
						T txn = db.startTransaction();
						try {
							c = db.addContact(txn, inSecret, outSecret, erase);
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
			contactLock.writeLock().unlock();
			// Erase the secrets after committing or aborting the transaction
			for(byte[] b : erase) ByteUtils.erase(b);
		}
		// Call the listeners outside the lock
		callListeners(new ContactAddedEvent(c));
		return c;
	}

	/** Notifies all listeners of a database event. */
	private void callListeners(DatabaseEvent e) {
		for(DatabaseListener d : listeners) d.eventOccurred(e);
	}

	public void addLocalGroupMessage(Message m) throws DbException {
		boolean added = false;
		contactLock.readLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				messageStatusLock.writeLock().lock();
				try {
					subscriptionLock.readLock().lock();
					try {
						T txn = db.startTransaction();
						try {
							// Don't store the message if the user has
							// unsubscribed from the group or the message
							// predates the subscription
							if(db.containsSubscription(txn, m.getGroup(),
									m.getTimestamp())) {
								added = storeGroupMessage(txn, m, null);
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
					messageStatusLock.writeLock().unlock();
				}
			} finally {
				messageLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		// Call the listeners outside the lock
		if(added) callListeners(new MessagesAddedEvent());
	}

	/**
	 * If the given message is already in the database, marks it as seen by the
	 * sender and returns false. Otherwise stores the message, updates the
	 * sendability of its ancestors if necessary, marks the message as seen by
	 * the sender and unseen by all other contacts, and returns true.
	 * <p>
	 * Locking: contact read, message write, messageStatus write.
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
	 * @param increment True if the message's sendability has changed from 0 to
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
			if(!containsContact(c)) throw new NoSuchContactException();
			messageLock.writeLock().lock();
			try {
				messageStatusLock.writeLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						added = storePrivateMessage(txn, m, c, false);
						db.commitTransaction(txn);
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					}
				} finally {
					messageStatusLock.writeLock().unlock();
				}
			} finally {
				messageLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		// Call the listeners outside the lock
		if(added) callListeners(new MessagesAddedEvent());
	}

	/**
	 * If the given message is already in the database, returns false.
	 * Otherwise stores the message and marks it as new or seen with respect to
	 * the given contact, depending on whether the message is outgoing or
	 * incoming, respectively.
	 * <p>
	 * Locking: contact read, message write, messageStatus write.
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

	/**
	 * Returns true if the database contains the given contact.
	 * <p>
	 * Locking: contact read.
	 */
	private boolean containsContact(ContactId c) throws DbException {
		T txn = db.startTransaction();
		try {
			boolean contains = db.containsContact(txn, c);
			db.commitTransaction(txn);
			return contains;
		} catch(DbException e) {
			db.abortTransaction(txn);
			throw e;
		}
	}

	public TransportIndex addTransport(TransportId t) throws DbException {
		TransportIndex i;
		transportLock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				i = db.addTransport(txn, t);
				db.commitTransaction(txn);
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			transportLock.writeLock().unlock();
		}
		// Call the listeners outside the lock
		if(i != null) callListeners(new TransportAddedEvent(t));
		return i;
	}

	public Ack generateAck(ContactId c, int maxBatches) throws DbException {
		Collection<BatchId> acked;
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			messageStatusLock.readLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					acked = db.getBatchesToAck(txn, c, maxBatches);
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				messageStatusLock.readLock().unlock();
			}
			if(acked.isEmpty()) return null;
			// Record the contents of the ack
			messageStatusLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					db.removeBatchesToAck(txn, c, acked);
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				messageStatusLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		return packetFactory.createAck(acked);
	}

	public RawBatch generateBatch(ContactId c, int capacity)
	throws DbException {
		Collection<MessageId> ids;
		List<byte[]> messages = new ArrayList<byte[]>();
		RawBatch b;
		// Get some sendable messages from the database
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			messageLock.readLock().lock();
			try {
				messageStatusLock.readLock().lock();
				try {
					subscriptionLock.readLock().lock();
					try {
						T txn = db.startTransaction();
						try {
							ids = db.getSendableMessages(txn, c, capacity);
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
					messageStatusLock.readLock().unlock();
				}
				if(messages.isEmpty()) return null;
				messages = Collections.unmodifiableList(messages);
				b = packetFactory.createBatch(messages);
				messageStatusLock.writeLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						db.addOutstandingBatch(txn, c, b.getId(), ids);
						db.commitTransaction(txn);
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					}
				} finally {
					messageStatusLock.writeLock().unlock();
				}
			} finally {
				messageLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		return b;
	}

	public RawBatch generateBatch(ContactId c, int capacity,
			Collection<MessageId> requested) throws DbException {
		Collection<MessageId> ids = new ArrayList<MessageId>();
		List<byte[]> messages = new ArrayList<byte[]>();
		RawBatch b;
		// Get some sendable messages from the database
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			messageLock.readLock().lock();
			try {
				messageStatusLock.readLock().lock();
				try{
					subscriptionLock.readLock().lock();
					try {
						T txn = db.startTransaction();
						try {
							Iterator<MessageId> it = requested.iterator();
							while(it.hasNext()) {
								MessageId m = it.next();
								byte[] raw = db.getMessageIfSendable(txn, c, m);
								if(raw != null) {
									if(raw.length > capacity) break;
									messages.add(raw);
									ids.add(m);
									capacity -= raw.length;
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
					messageStatusLock.readLock().unlock();
				}
				if(messages.isEmpty()) return null;
				messages = Collections.unmodifiableList(messages);
				b = packetFactory.createBatch(messages);
				messageStatusLock.writeLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						db.addOutstandingBatch(txn, c, b.getId(), ids);
						db.commitTransaction(txn);
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					}
				} finally {
					messageStatusLock.writeLock().unlock();
				}
			} finally {
				messageLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		return b;
	}

	public Offer generateOffer(ContactId c, int maxMessages)
	throws DbException {
		Collection<MessageId> offered;
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			messageLock.readLock().lock();
			try {
				messageStatusLock.readLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						offered = db.getOfferableMessages(txn, c, maxMessages);
						db.commitTransaction(txn);
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					}
				} finally {
					messageStatusLock.readLock().unlock();
				}
			} finally {
				messageLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		return packetFactory.createOffer(offered);
	}

	public SubscriptionUpdate generateSubscriptionUpdate(ContactId c)
	throws DbException {
		Map<GroupId, GroupId> holes;
		Map<Group, Long> subs;
		long expiry, timestamp;
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			subscriptionLock.readLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					timestamp = System.currentTimeMillis() - 1;
					holes = db.getVisibleHoles(txn, c, timestamp);
					subs = db.getVisibleSubscriptions(txn, c, timestamp);
					expiry = db.getExpiryTime(txn);
					db.commitTransaction(txn);
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
		return packetFactory.createSubscriptionUpdate(holes, subs, expiry,
				timestamp);
	}

	private boolean updateIsDue(long sent) {
		long now = System.currentTimeMillis();
		return now - sent >= DatabaseConstants.MAX_UPDATE_INTERVAL;
	}

	public TransportUpdate generateTransportUpdate(ContactId c)
	throws DbException {
		boolean due;
		Collection<Transport> transports;
		long timestamp;
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			transportLock.readLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					// Work out whether an update is due
					long modified = db.getTransportsModified(txn);
					long sent = db.getTransportsSent(txn, c);
					due = modified >= sent || updateIsDue(sent);
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				transportLock.readLock().unlock();
			}
			if(!due) return null;
			transportLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					transports = db.getLocalTransports(txn);
					timestamp = System.currentTimeMillis();
					db.setTransportsSent(txn, c, timestamp);
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
		return packetFactory.createTransportUpdate(transports, timestamp);
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

	public ConnectionContext getConnectionContext(ContactId c, TransportIndex i)
	throws DbException {
		Collection<byte[]> erase = new ArrayList<byte[]>();
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			windowLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					ConnectionContext ctx =
						db.getConnectionContext(txn, c, i, erase);
					db.commitTransaction(txn);
					return ctx;
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				windowLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
			// Erase the secrets after committing or aborting the transaction
			for(byte[] b : erase) ByteUtils.erase(b);
		}
	}

	public ConnectionWindow getConnectionWindow(ContactId c, TransportIndex i)
	throws DbException {
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			windowLock.readLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					ConnectionWindow w = db.getConnectionWindow(txn, c, i);
					db.commitTransaction(txn);
					return w;
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

	public TransportIndex getLocalIndex(TransportId t) throws DbException {
		transportLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				TransportIndex i = db.getLocalIndex(txn, t);
				db.commitTransaction(txn);
				return i;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			transportLock.readLock().unlock();
		}
	}

	public TransportProperties getLocalProperties(TransportId t)
	throws DbException {
		transportLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				TransportProperties p = db.getLocalProperties(txn, t);
				db.commitTransaction(txn);
				return p;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			transportLock.readLock().unlock();
		}
	}

	public Collection<Transport> getLocalTransports() throws DbException {
		transportLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Collection<Transport> transports = db.getLocalTransports(txn);
				db.commitTransaction(txn);
				return transports;
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
			messageFlagLock.readLock().lock();
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
				messageFlagLock.readLock().unlock();
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

	public TransportIndex getRemoteIndex(ContactId c, TransportId t)
	throws DbException {
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			transportLock.readLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					TransportIndex i = db.getRemoteIndex(txn, c, t);
					db.commitTransaction(txn);
					return i;
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
			messageFlagLock.readLock().lock();
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
				messageFlagLock.readLock().unlock();
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
			if(!containsContact(c)) throw new NoSuchContactException();
			messageLock.readLock().lock();
			try {
				messageStatusLock.readLock().lock();
				try {
					subscriptionLock.readLock().lock();
					try {
						T txn = db.startTransaction();
						try {
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
					messageStatusLock.readLock().unlock();
				}
			} finally {
				messageLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public void receiveAck(ContactId c, Ack a) throws DbException {
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			messageLock.readLock().lock();
			try {
				messageStatusLock.writeLock().lock();
				try {
					Collection<BatchId> acks = a.getBatchIds();
					T txn = db.startTransaction();
					try {
						// Mark all messages in acked batches as seen
						for(BatchId b : acks) db.removeAckedBatch(txn, c, b);
						// Find any lost batches that need to be retransmitted
						Collection<BatchId> lost = db.getLostBatches(txn, c);
						for(BatchId b : lost) db.removeLostBatch(txn, c, b);
						db.commitTransaction(txn);
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					}
				} finally {
					messageStatusLock.writeLock().unlock();
				}
			} finally {
				messageLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public void receiveBatch(ContactId c, Batch b) throws DbException {
		boolean anyAdded = false;
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			messageLock.writeLock().lock();
			try {
				messageStatusLock.writeLock().lock();
				try {
					subscriptionLock.readLock().lock();
					try {
						T txn = db.startTransaction();
						try {
							anyAdded = storeMessages(txn, c, b.getMessages());
							db.addBatchToAck(txn, c, b.getId());
							db.commitTransaction(txn);
						} catch(DbException e) {
							db.abortTransaction(txn);
							throw e;
						}
					} finally {
						subscriptionLock.readLock().unlock();
					}
				} finally {
					messageStatusLock.writeLock().unlock();
				}
			} finally {
				messageLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		// Call the listeners outside the lock
		callListeners(new BatchReceivedEvent());
		if(anyAdded) callListeners(new MessagesAddedEvent());
	}

	/**
	 * Attempts to store a collection of messages received from the given
	 * contact, and returns true if any were stored.
	 * <p>
	 * Locking: contact read, message write, messageStatus write,
	 * subscription read.
	 */
	private boolean storeMessages(T txn, ContactId c,
			Collection<Message> messages) throws DbException {
		boolean anyStored = false;
		for(Message m : messages) {
			GroupId g = m.getGroup();
			if(g == null) {
				if(storePrivateMessage(txn, m, c, true)) anyStored = true;
			} else {
				long timestamp = m.getTimestamp();
				if(db.containsVisibleSubscription(txn, g, c, timestamp)) {
					if(storeGroupMessage(txn, m, c)) anyStored = true;
				}
			}
		}
		return anyStored;
	}

	public Request receiveOffer(ContactId c, Offer o) throws DbException {
		Collection<MessageId> offered;
		BitSet request;
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			messageLock.readLock().lock();
			try {
				messageStatusLock.writeLock().lock();
				try {
					subscriptionLock.readLock().lock();
					try {
						T txn = db.startTransaction();
						try {
							offered = o.getMessageIds();
							request = new BitSet(offered.size());
							Iterator<MessageId> it = offered.iterator();
							for(int i = 0; it.hasNext(); i++) {
								// If the message is not in the database, or if
								// it is not visible to the contact, request it
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
					messageStatusLock.writeLock().unlock();
				}
			} finally {
				messageLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		return packetFactory.createRequest(request, offered.size());
	}

	public void receiveSubscriptionUpdate(ContactId c, SubscriptionUpdate s)
	throws DbException {
		// Update the contact's subscriptions
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			subscriptionLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					Map<Group, Long> subs = s.getSubscriptions();
					db.setSubscriptions(txn, c, subs, s.getTimestamp());
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
		callListeners(new SubscriptionsUpdatedEvent(
				Collections.singletonList(c)));
	}

	public void receiveTransportUpdate(ContactId c, TransportUpdate t)
	throws DbException {
		Collection<Transport> transports;
		// Update the contact's transport properties
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			transportLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					transports = t.getTransports();
					db.setTransports(txn, c, transports, t.getTimestamp());
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
		callListeners(new RemoteTransportsUpdatedEvent(c, transports));
	}

	public void removeContact(ContactId c) throws DbException {
		contactLock.writeLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			messageLock.writeLock().lock();
			try {
				messageFlagLock.writeLock().lock();
				try {
					messageStatusLock.writeLock().lock();
					try {
						subscriptionLock.writeLock().lock();
						try {
							transportLock.writeLock().lock();
							try {
								windowLock.writeLock().lock();
								try {
									T txn = db.startTransaction();
									try {
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
						messageStatusLock.writeLock().unlock();
					}
				} finally {
					messageFlagLock.writeLock().unlock();
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

	public void setConfig(TransportId t, TransportConfig c)
	throws DbException {
		transportLock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				db.setConfig(txn, t, c);
				db.commitTransaction(txn);
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			transportLock.writeLock().unlock();
		}
	}

	public void setConnectionWindow(ContactId c, TransportIndex i,
			ConnectionWindow w) throws DbException {
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			windowLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					db.setConnectionWindow(txn, c, i, w);
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
				}
			} finally {
				windowLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public void setLocalProperties(TransportId t, TransportProperties p)
	throws DbException {
		boolean changed = false;
		transportLock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if(!p.equals(db.getLocalProperties(txn, t))) {
					db.setLocalProperties(txn, t, p);
					db.setTransportsModified(txn, System.currentTimeMillis());
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
			if(!containsContact(c)) throw new NoSuchContactException();
			messageLock.readLock().lock();
			try {
				messageStatusLock.writeLock().lock();
				try {
					subscriptionLock.readLock().lock();
					try {
						T txn = db.startTransaction();
						try {
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
					messageStatusLock.writeLock().unlock();
				}
			} finally {
				messageLock.readLock().unlock();
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
	 * @param increment True if the user's rating for the author has changed
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
		List<ContactId> affected = new ArrayList<ContactId>();
		contactLock.readLock().lock();
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
		// Call the listeners outside the lock
		if(!affected.isEmpty()) {
			affected = Collections.unmodifiableList(affected);
			callListeners(new SubscriptionsUpdatedEvent(affected));
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
		contactLock.readLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				messageFlagLock.writeLock().lock();
				try {
					messageStatusLock.writeLock().lock();
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
						messageStatusLock.writeLock().unlock();
					}
				} finally {
					messageFlagLock.writeLock().unlock();
				}
			} finally {
				messageLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		// Call the listeners outside the lock
		if(affected != null && !affected.isEmpty())
			callListeners(new SubscriptionsUpdatedEvent(affected));
	}

	public void checkFreeSpaceAndClean() throws DbException {
		long freeSpace = db.getFreeSpace();
		while(freeSpace < MIN_FREE_SPACE) {
			boolean expired = expireMessages(BYTES_PER_SWEEP);
			if(freeSpace < CRITICAL_FREE_SPACE && !expired) {
				// FIXME: Work out what to do here - the amount of free space
				// is critically low and there are no messages left to expire
				System.err.println("Disk space is critical - shutting down");
				System.exit(1);
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
		Collection<MessageId> old;
		contactLock.readLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				messageFlagLock.writeLock().lock();
				try {
					messageStatusLock.writeLock().lock();
					try {
						T txn = db.startTransaction();
						try {
							old = db.getOldMessages(txn, size);
							for(MessageId m : old) removeMessage(txn, m);
							db.commitTransaction(txn);
						} catch(DbException e) {
							db.abortTransaction(txn);
							throw e;
						}
					} finally {
						messageStatusLock.writeLock().unlock();
					}
				} finally {
					messageFlagLock.writeLock().unlock();
				}
			} finally {
				messageLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		return old.isEmpty();
	}

	/**
	 * Removes the given message (and all associated state) from the database.
	 * <p>
	 * Locking: contact read, message write, messageStatus write.
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
			long now = System.currentTimeMillis();
			if(bytesStoredSinceLastCheck > MAX_BYTES_BETWEEN_SPACE_CHECKS
					|| now - timeOfLastCheck > MAX_MS_BETWEEN_SPACE_CHECKS) {
				bytesStoredSinceLastCheck = 0L;
				timeOfLastCheck = now;
				return true;
			}
		}
		return false;
	}
}
