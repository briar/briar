package net.sf.briar.db;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.Rating;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.NoSuchContactException;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.BundleReader;
import net.sf.briar.api.protocol.BundleWriter;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Header;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.serial.Raw;
import net.sf.briar.api.serial.RawByteArray;

import com.google.inject.Inject;

/**
 * An implementation of DatabaseComponent using Java synchronization. This
 * implementation does not distinguish between readers and writers.
 */
class SynchronizedDatabaseComponent<Txn> extends DatabaseComponentImpl<Txn> {

	private static final Logger LOG =
		Logger.getLogger(SynchronizedDatabaseComponent.class.getName());

	/*
	 * Locks must always be acquired in alphabetical order. See the Database
	 * interface to find out which calls require which locks.
	 */

	private final Object contactLock = new Object();
	private final Object messageLock = new Object();
	private final Object messageStatusLock = new Object();
	private final Object ratingLock = new Object();
	private final Object subscriptionLock = new Object();
	private final Object transportLock = new Object();

	@Inject
	SynchronizedDatabaseComponent(Database<Txn> db, DatabaseCleaner cleaner) {
		super(db, cleaner);
	}

	public void close() throws DbException {
		cleaner.stopCleaning();
		synchronized(contactLock) {
			synchronized(messageLock) {
				synchronized(messageStatusLock) {
					synchronized(ratingLock) {
						synchronized(subscriptionLock) {
							synchronized(transportLock) {
								db.close();
							}
						}
					}
				}
			}
		}
	}

	public ContactId addContact(Map<String, String> transports)
	throws DbException {
		if(LOG.isLoggable(Level.FINE)) LOG.fine("Adding contact");
		synchronized(contactLock) {
			synchronized(transportLock) {
				Txn txn = db.startTransaction();
				try {
					ContactId c = db.addContact(txn, transports);
					db.commitTransaction(txn);
					if(LOG.isLoggable(Level.FINE))
						LOG.fine("Added contact " + c);
					return c;
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			}
		}
	}

	public void addLocallyGeneratedMessage(Message m) throws DbException {
		waitForPermissionToWrite();
		synchronized(contactLock) {
			synchronized(messageLock) {
				synchronized(messageStatusLock) {
					synchronized(subscriptionLock) {
						Txn txn = db.startTransaction();
						try {
							// Don't store the message if the user has
							// unsubscribed from the group
							if(db.containsSubscription(txn, m.getGroup())) {
								boolean added = storeMessage(txn, m, null);
								if(!added) {
									if(LOG.isLoggable(Level.FINE))
										LOG.fine("Duplicate local message");
								}
							} else {
								if(LOG.isLoggable(Level.FINE))
									LOG.fine("Not subscribed");
							}
							db.commitTransaction(txn);
						} catch(DbException e) {
							db.abortTransaction(txn);
							throw e;
						}
					}
				}
			}
		}
	}

	protected void expireMessages(long size) throws DbException {
		synchronized(contactLock) {
			synchronized(messageLock) {
				synchronized(messageStatusLock) {
					Txn txn = db.startTransaction();
					try {
						for(MessageId m : db.getOldMessages(txn, size)) {
							removeMessage(txn, m);
						}
						db.commitTransaction(txn);
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					}
				}
			}
		}
	}

	public void generateBundle(ContactId c, BundleWriter b) throws DbException,
	IOException, GeneralSecurityException {
		if(LOG.isLoggable(Level.FINE)) LOG.fine("Generating bundle for " + c);
		Set<BatchId> acks;
		Set<GroupId> subs;
		Map<String, String> transports;
		// Add acks
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(messageStatusLock) {
				Txn txn = db.startTransaction();
				try {
					acks = db.removeBatchesToAck(txn, c);
					if(LOG.isLoggable(Level.FINE))
						LOG.fine("Added " + acks.size() + " acks");
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			}
		}
		// Add subscriptions
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(subscriptionLock) {
				Txn txn = db.startTransaction();
				try {
					subs = db.getSubscriptions(txn);
					if(LOG.isLoggable(Level.FINE))
						LOG.fine("Added " + subs.size() + " subscriptions");
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			}
		}
		// Add transport details
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(transportLock) {
				Txn txn = db.startTransaction();
				try {
					transports = db.getTransports(txn);
					if(LOG.isLoggable(Level.FINE))
						LOG.fine("Added " + transports.size() + " transports");
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			}
		}
		// Add the header to the bundle
		b.addHeader(acks, subs, transports);
		// Add as many messages as possible to the bundle
		while(fillBatch(c, b));
		b.finish();
		if(LOG.isLoggable(Level.FINE)) LOG.fine("Bundle generated");
		System.gc();
	}

	private boolean fillBatch(ContactId c, BundleWriter b) throws DbException,
	IOException, GeneralSecurityException {
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(messageLock) {
				synchronized(messageStatusLock) {
					Txn txn = db.startTransaction();
					try {
						long capacity =
							Math.min(b.getRemainingCapacity(), Batch.MAX_SIZE);
						Iterator<MessageId> it =
							db.getSendableMessages(txn, c, capacity).iterator();
						if(!it.hasNext()) {
							db.commitTransaction(txn);
							return false; // No more messages to send
						}
						Set<MessageId> sent = new HashSet<MessageId>();
						List<Raw> messages = new ArrayList<Raw>();
						int bytesSent = 0;
						while(it.hasNext()) {
							MessageId m = it.next();
							byte[] message = db.getMessage(txn, m);
							bytesSent += message.length;
							messages.add(new RawByteArray(message));
							sent.add(m);
						}
						BatchId batchId = b.addBatch(messages);
						// Record the contents of the batch
						assert !sent.isEmpty();
						db.addOutstandingBatch(txn, c, batchId, sent);
						db.commitTransaction(txn);
						// Don't create another batch if this one was half-empty
						return bytesSent > Batch.MAX_SIZE / 2;
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					} catch(SignatureException e) {
						db.abortTransaction(txn);
						throw e;
					}
				}
			}
		}
	}

	public Set<ContactId> getContacts() throws DbException {
		synchronized(contactLock) {
			Txn txn = db.startTransaction();
			try {
				Set<ContactId> contacts = db.getContacts(txn);
				db.commitTransaction(txn);
				return contacts;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		}
	}

	public Rating getRating(AuthorId a) throws DbException {
		synchronized(ratingLock) {
			Txn txn = db.startTransaction();
			try {
				Rating r = db.getRating(txn, a);
				db.commitTransaction(txn);
				return r;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		}
	}

	public Set<GroupId> getSubscriptions() throws DbException {
		synchronized(subscriptionLock) {
			Txn txn = db.startTransaction();
			try {
				Set<GroupId> subs = db.getSubscriptions(txn);
				db.commitTransaction(txn);
				return subs;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		}
	}

	public Map<String, String> getTransports() throws DbException {
		synchronized(transportLock) {
			Txn txn = db.startTransaction();
			try {
				Map<String, String> transports = db.getTransports(txn);
				db.commitTransaction(txn);
				return transports;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		}
	}

	public Map<String, String> getTransports(ContactId c) throws DbException {
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(transportLock) {
				Txn txn = db.startTransaction();
				try {
					Map<String, String> transports = db.getTransports(txn, c);
					db.commitTransaction(txn);
					return transports;
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			}
		}
	}

	public void receiveBundle(ContactId c, BundleReader b) throws DbException,
	IOException, GeneralSecurityException {
		if(LOG.isLoggable(Level.FINE)) LOG.fine("Received bundle from " + c);
		Header h;
		// Mark all messages in acked batches as seen
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			h = b.getHeader();
			synchronized(messageLock) {
				synchronized(messageStatusLock) {
					Set<BatchId> acks = h.getAcks();
					for(BatchId ack : acks) {
						Txn txn = db.startTransaction();
						try {
							db.removeAckedBatch(txn, c, ack);
							db.commitTransaction(txn);
						} catch(DbException e) {
							db.abortTransaction(txn);
							throw e;
						}
					}
					if(LOG.isLoggable(Level.FINE))
						LOG.fine("Received " + acks.size() + " acks");
				}
			}
		}
		// Update the contact's subscriptions
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(subscriptionLock) {
				Txn txn = db.startTransaction();
				try {
					Set<GroupId> subs = h.getSubscriptions();
					db.setSubscriptions(txn, c, subs);
					if(LOG.isLoggable(Level.FINE))
						LOG.fine("Received " + subs.size() + " subscriptions");
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			}
		}
		// Update the contact's transport details
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(transportLock) {
				Txn txn = db.startTransaction();
				try {
					Map<String, String> transports = h.getTransports();
					db.setTransports(txn, c, transports);
					if(LOG.isLoggable(Level.FINE))
						LOG.fine("Received " + transports.size()
								+ " transports");
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			}
		}
		// Store the messages
		int batches = 0;
		Batch batch = null;
		while((batch = b.getNextBatch()) != null) {
			storeBatch(c, batch);
			batches++;
		}
		if(LOG.isLoggable(Level.FINE))
			LOG.fine("Received " + batches + " batches");
		b.finish();
		findLostBatches(c);
		System.gc();
	}

	private void storeBatch(ContactId c, Batch b) throws DbException {
		waitForPermissionToWrite();
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(messageLock) {
				synchronized(messageStatusLock) {
					synchronized(subscriptionLock) {
						Txn txn = db.startTransaction();
						try {
							int received = 0, stored = 0;
							for(Message m : b.getMessages()) {
								received++;
								GroupId g = m.getGroup();
								if(db.containsSubscription(txn, g)) {
									if(storeMessage(txn, m, c)) stored++;
								}
							}
							if(LOG.isLoggable(Level.FINE))
								LOG.fine("Received " + received
										+ " messages, stored " + stored);
							db.addBatchToAck(txn, c, b.getId());
							db.commitTransaction(txn);
						} catch(DbException e) {
							db.abortTransaction(txn);
							throw e;
						}
					}
				}
			}
		}
	}

	private void findLostBatches(ContactId c)
	throws DbException {
		// Find any lost batches that need to be retransmitted
		Set<BatchId> lost;
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(messageLock) {
				synchronized(messageStatusLock) {
					Txn txn = db.startTransaction();
					try {
						lost = db.getLostBatches(txn, c);
						db.commitTransaction(txn);
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					}
				}
			}
		}
		for(BatchId batch : lost) {
			synchronized(contactLock) {
				if(!containsContact(c)) throw new NoSuchContactException();
				synchronized(messageLock) {
					synchronized(messageStatusLock) {
						Txn txn = db.startTransaction();
						try {
							if(LOG.isLoggable(Level.FINE))
								LOG.fine("Removing lost batch");
							db.removeLostBatch(txn, c, batch);
							db.commitTransaction(txn);
						} catch(DbException e) {
							db.abortTransaction(txn);
							throw e;
						}
					}
				}
			}
		}
	}

	public void removeContact(ContactId c) throws DbException {
		if(LOG.isLoggable(Level.FINE)) LOG.fine("Removing contact " + c);
		synchronized(contactLock) {
			synchronized(messageStatusLock) {
				synchronized(subscriptionLock) {
					synchronized(transportLock) {
						Txn txn = db.startTransaction();
						try {
							db.removeContact(txn, c);
							db.commitTransaction(txn);
						} catch(DbException e) {
							db.abortTransaction(txn);
							throw e;
						}
					}
				}
			}
		}
	}

	public void setRating(AuthorId a, Rating r) throws DbException {
		synchronized(messageLock) {
			synchronized(ratingLock) {
				Txn txn = db.startTransaction();
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
			}
		}
	}

	public void setTransports(ContactId c, Map<String, String> transports)
	throws DbException {
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(transportLock) {
				Txn txn = db.startTransaction();
				try {
					db.setTransports(txn, c, transports);
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			}
		}
	}

	public void subscribe(GroupId g) throws DbException {
		if(LOG.isLoggable(Level.FINE)) LOG.fine("Subscribing to " + g);
		synchronized(subscriptionLock) {
			Txn txn = db.startTransaction();
			try {
				db.addSubscription(txn, g);
				db.commitTransaction(txn);
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		}
	}

	public void unsubscribe(GroupId g) throws DbException {
		if(LOG.isLoggable(Level.FINE)) LOG.fine("Unsubscribing from " + g);
		synchronized(contactLock) {
			synchronized(messageLock) {
				synchronized(messageStatusLock) {
					synchronized(subscriptionLock) {
						Txn txn = db.startTransaction();
						try {
							db.removeSubscription(txn, g);
							db.commitTransaction(txn);
						} catch(DbException e) {
							db.abortTransaction(txn);
							throw e;
						}
					}
				}
			}
		}
	}
}
