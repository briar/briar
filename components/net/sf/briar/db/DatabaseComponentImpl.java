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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.Bytes;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.Rating;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseListener;
import net.sf.briar.api.db.DatabaseListener.Event;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.NoSuchContactException;
import net.sf.briar.api.db.Status;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.protocol.writers.AckWriter;
import net.sf.briar.api.protocol.writers.BatchWriter;
import net.sf.briar.api.protocol.writers.OfferWriter;
import net.sf.briar.api.protocol.writers.RequestWriter;
import net.sf.briar.api.protocol.writers.SubscriptionWriter;
import net.sf.briar.api.protocol.writers.TransportWriter;
import net.sf.briar.api.transport.ConnectionWindow;

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

	private final List<DatabaseListener> listeners =
		new ArrayList<DatabaseListener>(); // Locking: self
	private final Object spaceLock = new Object();
	private final Object writeLock = new Object();
	private long bytesStoredSinceLastCheck = 0L; // Locking: spaceLock
	private long timeOfLastCheck = 0L; // Locking: spaceLock
	private volatile boolean writesAllowed = true;

	@Inject
	DatabaseComponentImpl(Database<T> db, DatabaseCleaner cleaner) {
		this.db = db;
		this.cleaner = cleaner;
	}

	public void open(boolean resume) throws DbException {
		db.open(resume);
		cleaner.startCleaning(this, MAX_MS_BETWEEN_SPACE_CHECKS);
	}

	public void close() throws DbException {
		cleaner.stopCleaning();
		db.close();
	}

	public void addListener(DatabaseListener d) {
		synchronized(listeners) {
			listeners.add(d);
		}
	}

	public void removeListener(DatabaseListener d) {
		synchronized(listeners) {
			listeners.remove(d);
		}
	}

	public ContactId addContact(Map<String, Map<String, String>> transports,
			byte[] secret) throws DbException {
		if(LOG.isLoggable(Level.FINE)) LOG.fine("Adding contact");
		ContactId c;
		contactLock.writeLock().lock();
		try {
			transportLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					c = db.addContact(txn, transports, secret);
					db.commitTransaction(txn);
					if(LOG.isLoggable(Level.FINE))
						LOG.fine("Added contact " + c);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				transportLock.writeLock().unlock();
			}
		} finally {
			contactLock.writeLock().unlock();
		}
		// Call the listeners outside the lock
		callListeners(Event.CONTACTS_UPDATED);
		return c;
	}

	/** Notifies all listeners of a database event. */
	private void callListeners(DatabaseListener.Event e) {
		synchronized(listeners) {
			if(!listeners.isEmpty()) {
				// Shuffle the listeners so we don't always send new messages
				// to contacts in the same order
				Collections.shuffle(listeners);
				for(DatabaseListener d : listeners) d.eventOccurred(e);
			}
		}
	}

	public void addLocalGroupMessage(Message m) throws DbException {
		boolean added = false;
		waitForPermissionToWrite();
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
		if(added) callListeners(Event.MESSAGES_ADDED);
	}

	/**
	 * Blocks until messages are allowed to be stored in the database. The
	 * storage of messages is not allowed while the amount of free storage
	 * space available to the database is less than CRITICAL_FREE_SPACE.
	 */
	private void waitForPermissionToWrite() {
		synchronized(writeLock) {
			while(!writesAllowed) {
				if(LOG.isLoggable(Level.FINE))
					LOG.fine("Waiting for permission to write");
				try {
					writeLock.wait();
				} catch(InterruptedException ignored) {}
			}
		}
	}

	/**
	 * If the given message is already in the database, marks it as seen by the
	 * sender and returns false. Otherwise stores the message, updates the
	 * sendability of its ancestors if necessary, marks the message as seen by
	 * the sender and unseen by all other contacts, and returns true.
	 * <p>
	 * Locking: contacts read, messages write, messageStatuses write.
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
				bytesStoredSinceLastCheck += m.getSize();
			}
		}
		return stored;
	}

	/**
	 * Calculates and returns the sendability score of a message.
	 * <p>
	 * Locking: messages write.
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
	 * Locking: messages write.
	 * @param increment True if the message's sendability has changed from 0 to
	 * greater than 0, or false if it has changed from greater than 0 to 0.
	 */
	private int updateAncestorSendability(T txn, MessageId m, boolean increment)
	throws DbException {
		GroupId group = db.getGroup(txn, m);
		int affected = 0;
		boolean changed = true;
		while(changed) {
			// Stop if the message has no parent
			MessageId parent = db.getParent(txn, m);
			if(parent == null) break;
			// Stop if the parent isn't in the database
			if(!db.containsMessage(txn, parent)) break;
			// Stop if the message and the parent aren't in the same group
			assert group != null;
			GroupId parentGroup = db.getGroup(txn, parent);
			if(!group.equals(parentGroup)) break;
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
			group = parentGroup;
		}
		return affected;
	}

	public void addLocalPrivateMessage(Message m, ContactId c)
	throws DbException {
		boolean added = false;
		waitForPermissionToWrite();
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
		if(added) callListeners(Event.MESSAGES_ADDED);
	}

	/**
	 * If the given message is already in the database, returns false.
	 * Otherwise stores the message and marks it as new or seen with respect to
	 * the given contact, depending on whether the message is outgoing or
	 * incoming, respectively.
	 * <p>
	 * Locking: contacts read, messages write, messageStatuses write.
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
			bytesStoredSinceLastCheck += m.getSize();
		}
		return true;
	}

	/**
	 * Returns true if the database contains the given contact.
	 * <p>
	 * Locking: contacts read.
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

	public boolean generateAck(ContactId c, AckWriter a) throws DbException,
	IOException {
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			Collection<BatchId> acks, sent = new ArrayList<BatchId>();
			messageStatusLock.readLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					acks = db.getBatchesToAck(txn, c);
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			} finally {
				messageStatusLock.readLock().unlock();
			}
			for(BatchId b : acks) {
				if(!a.writeBatchId(b)) break;
				sent.add(b);
			}
			if(LOG.isLoggable(Level.FINE))
				LOG.fine("Added " + sent.size() + " batch IDs to ack");
			// Record the contents of the ack, unless it's empty
			if(sent.isEmpty()) return false;
			a.finish();
			messageStatusLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					db.removeBatchesToAck(txn, c, sent);
					db.commitTransaction(txn);
					return true;
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
	}

	public boolean generateBatch(ContactId c, BatchWriter b) throws DbException,
	IOException {
		Collection<MessageId> ids = new ArrayList<MessageId>();
		Collection<Bytes> messages = new ArrayList<Bytes>();
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
							int capacity = b.getCapacity();
							ids = db.getSendableMessages(txn, c, capacity);
							for(MessageId m : ids) {
								byte[] raw = db.getMessage(txn, m);
								messages.add(new Bytes(raw));
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
			} finally {
				messageLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		if(ids.isEmpty()) return false;
		writeAndRecordBatch(c, b, ids, messages);
		return true;
	}

	private void writeAndRecordBatch(ContactId c, BatchWriter b,
			Collection<MessageId> ids, Collection<Bytes> messages)
	throws DbException, IOException {
		assert !ids.isEmpty();
		assert !messages.isEmpty();
		assert ids.size() == messages.size();
		// Add the messages to the batch
		for(Bytes raw : messages) {
			boolean written = b.writeMessage(raw.getBytes());
			assert written;
		}
		BatchId id = b.finish();
		// Record the contents of the batch
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			messageLock.readLock().lock();
			try {
				messageStatusLock.writeLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						db.addOutstandingBatch(txn, c, id, ids);
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

	public boolean generateBatch(ContactId c, BatchWriter b,
			Collection<MessageId> requested) throws DbException, IOException {
		Collection<MessageId> ids = new ArrayList<MessageId>();
		Collection<Bytes> messages = new ArrayList<Bytes>();
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
							int capacity = b.getCapacity();
							Iterator<MessageId> it = requested.iterator();
							while(it.hasNext()) {
								MessageId m = it.next();
								byte[] raw = db.getMessageIfSendable(txn, c, m);
								if(raw != null) {
									if(raw.length > capacity) break;
									ids.add(m);
									messages.add(new Bytes(raw));
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
			} finally {
				messageLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		if(ids.isEmpty()) return false;
		writeAndRecordBatch(c, b, ids, messages);
		return true;
	}

	public Collection<MessageId> generateOffer(ContactId c, OfferWriter o)
	throws DbException, IOException {
		Collection<MessageId> sendable, sent = new ArrayList<MessageId>();
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			messageLock.readLock().lock();
			try {
				messageStatusLock.readLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						sendable = db.getSendableMessages(txn, c);
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
		for(MessageId m : sendable) {
			if(!o.writeMessageId(m)) break;
			sent.add(m);
		}
		if(!sent.isEmpty()) o.finish();
		if(LOG.isLoggable(Level.FINE))
			LOG.fine("Added " + sent.size() + " message IDs to offer");
		return sent;
	}

	public void generateSubscriptionUpdate(ContactId c, SubscriptionWriter s)
	throws DbException, IOException {
		Map<Group, Long> subs;
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			subscriptionLock.readLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					subs = db.getVisibleSubscriptions(txn, c);
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
		s.writeSubscriptions(subs, System.currentTimeMillis());
		if(LOG.isLoggable(Level.FINE))
			LOG.fine("Added " + subs.size() + " subscriptions to update");
	}

	public void generateTransportUpdate(ContactId c, TransportWriter t)
	throws DbException, IOException {
		Map<String, Map<String, String>> transports;
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			transportLock.readLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					transports = db.getTransports(txn);
					db.commitTransaction(txn);
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
		t.writeTransports(transports, System.currentTimeMillis());
		if(LOG.isLoggable(Level.FINE))
			LOG.fine("Added " + transports.size() + " transports to update");
	}

	public ConnectionWindow getConnectionWindow(ContactId c, int transportId)
	throws DbException {
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			windowLock.readLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					ConnectionWindow w =
						db.getConnectionWindow(txn, c, transportId);
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

	public byte[] getSharedSecret(ContactId c) throws DbException {
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			T txn = db.startTransaction();
			try {
				byte[] secret = db.getSharedSecret(txn, c);
				db.commitTransaction(txn);
				return secret;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
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

	public Map<String, String> getTransportConfig(String name)
	throws DbException {
		transportLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Map<String, String> config = db.getTransportConfig(txn, name);
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

	public Map<String, Map<String, String>> getTransports() throws DbException {
		transportLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Map<String, Map<String, String>> transports =
					db.getTransports(txn);
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

	public Map<String, Map<String, String>> getTransports(ContactId c)
	throws DbException {
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			transportLock.readLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					Map<String, Map<String, String>> transports =
						db.getTransports(txn, c);
					db.commitTransaction(txn);
					return transports;
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
						if(LOG.isLoggable(Level.FINE))
							LOG.fine("Received " + acks.size() + " acks");
						for(BatchId b : acks) db.removeAckedBatch(txn, c, b);
						// Find any lost batches that need to be retransmitted
						Collection<BatchId> lost = db.getLostBatches(txn, c);
						if(LOG.isLoggable(Level.FINE))
							LOG.fine(lost.size() + " lost batches");
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
		waitForPermissionToWrite();
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
		if(anyAdded) callListeners(Event.MESSAGES_ADDED);
	}

	/**
	 * Attempts to store a collection of messages received from the given
	 * contact, and returns true if any were stored.
	 * <p>
	 * Locking: contacts read, messages write, messageStatuses write,
	 * subscriptions read.
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

	public void receiveOffer(ContactId c, Offer o, RequestWriter r)
	throws DbException, IOException {
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
		r.writeRequest(request, offered.size());
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
					if(LOG.isLoggable(Level.FINE))
						LOG.fine("Received " + subs.size() + " subscriptions");
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

	public void receiveTransportUpdate(ContactId c, TransportUpdate t)
	throws DbException {
		// Update the contact's transport properties
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			transportLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					Map<String, Map<String, String>> transports =
						t.getTransports();
					db.setTransports(txn, c, transports, t.getTimestamp());
					if(LOG.isLoggable(Level.FINE))
						LOG.fine("Received " + transports.size()
								+ " transports");
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

	public void removeContact(ContactId c) throws DbException {
		if(LOG.isLoggable(Level.FINE)) LOG.fine("Removing contact " + c);
		contactLock.writeLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				messageStatusLock.writeLock().lock();
				try {
					subscriptionLock.writeLock().lock();
					try {
						transportLock.writeLock().lock();
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
							transportLock.writeLock().unlock();
						}
					} finally {
						subscriptionLock.writeLock().unlock();
					}
				} finally {
					messageStatusLock.writeLock().unlock();
				}
			} finally {
				messageLock.writeLock().unlock();
			}
		} finally {
			contactLock.writeLock().unlock();
		}
		// Call the listeners outside the lock
		callListeners(Event.CONTACTS_UPDATED);
	}

	public void setConnectionWindow(ContactId c, int transportId,
			ConnectionWindow w) throws DbException {
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			windowLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					db.setConnectionWindow(txn, c, transportId, w);
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

	public void setRating(AuthorId a, Rating r) throws DbException {
		messageLock.writeLock().lock();
		try {
			ratingLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					Rating old = db.setRating(txn, a, r);
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
	 * Locking: messages write.
	 * @param increment True if the user's rating for the author has changed
	 * from not good to good, or false if it has changed from good to not good.
	 */
	private void updateAuthorSendability(T txn, AuthorId a, boolean increment)
	throws DbException {
		int direct = 0, indirect = 0;
		for(MessageId id : db.getMessagesByAuthor(txn, a)) {
			int sendability = db.getSendability(txn, id);
			if(increment) {
				db.setSendability(txn, id, sendability + 1);
				if(sendability == 0) {
					direct++;
					indirect += updateAncestorSendability(txn, id, true);
				}
			} else {
				assert sendability > 0;
				db.setSendability(txn, id, sendability - 1);
				if(sendability == 1) {
					direct++;
					indirect += updateAncestorSendability(txn, id, false);
				}
			}
		}
		if(LOG.isLoggable(Level.FINE))
			LOG.fine(direct + " messages affected directly, "
					+ indirect + " indirectly");
	}

	public void setTransportConfig(String name,
			Map<String, String> config) throws DbException {
		boolean changed = false;
		transportLock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Map<String, String> old = db.getTransportConfig(txn, name);
				if(!config.equals(old)) {
					db.setTransportConfig(txn, name, config);
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
		if(changed) callListeners(Event.TRANSPORTS_UPDATED);
	}

	public void setTransportProperties(String name,
			Map<String, String> properties) throws DbException {
		boolean changed = false;
		transportLock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Map<String, String> old = db.getTransports(txn).get(name);
				if(!properties.equals(old)) {
					db.setTransportProperties(txn, name, properties);
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
		if(changed) callListeners(Event.TRANSPORTS_UPDATED);
	}

	public void setVisibility(GroupId g, Collection<ContactId> visible)
	throws DbException {
		contactLock.readLock().lock();
		try {
			subscriptionLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					// Remove any ex-contacts from the set
					Collection<ContactId> present =
						new ArrayList<ContactId>(visible.size());
					for(ContactId c : visible) {
						if(db.containsContact(txn, c)) present.add(c);
					}
					db.setVisibility(txn, g, present);
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

	public void subscribe(Group g) throws DbException {
		if(LOG.isLoggable(Level.FINE)) LOG.fine("Subscribing to " + g);
		boolean added = false;
		subscriptionLock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if(db.containsSubscription(txn, g.getId())) {
					db.addSubscription(txn, g);
					added = true;
				}
				db.commitTransaction(txn);
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			subscriptionLock.writeLock().unlock();
		}
		// Call the listeners outside the lock
		if(added) callListeners(Event.SUBSCRIPTIONS_UPDATED);
	}

	public void unsubscribe(GroupId g) throws DbException {
		if(LOG.isLoggable(Level.FINE)) LOG.fine("Unsubscribing from " + g);
		boolean removed = false;
		contactLock.readLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				messageStatusLock.writeLock().lock();
				try {
					subscriptionLock.writeLock().lock();
					try {
						T txn = db.startTransaction();
						try {
							if(db.containsSubscription(txn, g)) {
								db.removeSubscription(txn, g);
								removed = true;
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
				messageLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		// Call the listeners outside the lock
		if(removed) callListeners(Event.SUBSCRIPTIONS_UPDATED);
	}

	public void checkFreeSpaceAndClean() throws DbException {
		long freeSpace = db.getFreeSpace();
		while(freeSpace < MIN_FREE_SPACE) {
			// If disk space is critical, disable the storage of new messages
			if(freeSpace < CRITICAL_FREE_SPACE) {
				if(LOG.isLoggable(Level.FINE)) LOG.fine("Critical cleanup");
				writesAllowed = false;
			} else {
				if(LOG.isLoggable(Level.FINE)) LOG.fine("Normal cleanup");
			}
			expireMessages(BYTES_PER_SWEEP);
			Thread.yield();
			freeSpace = db.getFreeSpace();
			// If disk space is no longer critical, re-enable writes
			if(freeSpace >= CRITICAL_FREE_SPACE && !writesAllowed) {
				writesAllowed = true;
				synchronized(writeLock) {
					writeLock.notifyAll();
				}
			}
		}
	}

	private void expireMessages(int size) throws DbException {
		contactLock.readLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				messageStatusLock.writeLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						for(MessageId m : db.getOldMessages(txn, size)) {
							removeMessage(txn, m);
						}
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
	}

	/**
	 * Removes the given message (and all associated state) from the database. 
	 * <p>
	 * Locking: contacts read, messages write, messageStatuses write.
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
			if(bytesStoredSinceLastCheck > MAX_BYTES_BETWEEN_SPACE_CHECKS) {
				if(LOG.isLoggable(Level.FINE))
					LOG.fine(bytesStoredSinceLastCheck
							+ " bytes stored since last check");
				bytesStoredSinceLastCheck = 0L;
				timeOfLastCheck = now;
				return true;
			}
			if(now - timeOfLastCheck > MAX_MS_BETWEEN_SPACE_CHECKS) {
				if(LOG.isLoggable(Level.FINE))
					LOG.fine((now - timeOfLastCheck) + " ms since last check");
				bytesStoredSinceLastCheck = 0L;
				timeOfLastCheck = now;
				return true;
			}
		}
		return false;
	}
}
