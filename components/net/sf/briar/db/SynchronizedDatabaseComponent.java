package net.sf.briar.db;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SignatureException;
import java.util.HashSet;
import java.util.Iterator;
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
import net.sf.briar.api.protocol.BatchBuilder;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.BundleId;
import net.sf.briar.api.protocol.BundleReader;
import net.sf.briar.api.protocol.BundleWriter;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Header;
import net.sf.briar.api.protocol.HeaderBuilder;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;

import com.google.inject.Inject;
import com.google.inject.Provider;

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
	SynchronizedDatabaseComponent(Database<Txn> db, DatabaseCleaner cleaner,
			Provider<HeaderBuilder> headerBuilderProvider,
			Provider<BatchBuilder> batchBuilderProvider) {
		super(db, cleaner, headerBuilderProvider, batchBuilderProvider);
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
		HeaderBuilder h;
		// Add acks
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			h = headerBuilderProvider.get();
			synchronized(messageStatusLock) {
				Txn txn = db.startTransaction();
				try {
					Set<BatchId> acks = db.removeBatchesToAck(txn, c);
					h.addAcks(acks);
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
					Set<GroupId> subs = db.getSubscriptions(txn);
					h.addSubscriptions(subs);
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
					Map<String, String> transports = db.getTransports(txn);
					h.addTransports(transports);
					if(LOG.isLoggable(Level.FINE))
						LOG.fine("Added " + transports.size() + " transports");
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			}
		}
		// Sign the header and add it to the bundle
		Header header = h.build();
		long capacity = b.getCapacity();
		capacity -= header.getSize();
		b.addHeader(header);
		// Add as many messages as possible to the bundle
		while(true) {
			Batch batch = fillBatch(c, capacity);
			if(batch == null) break; // No more messages to send
			b.addBatch(batch);
			long size = batch.getSize();
			capacity -= size;
			// If the batch is less than half full, stop trying - there may be
			// more messages trickling in but we can't wait forever
			if(size * 2 < Batch.MAX_SIZE) break;
		}
		b.close();
		if(LOG.isLoggable(Level.FINE)) LOG.fine("Bundle generated");
		System.gc();
	}

	private Batch fillBatch(ContactId c, long capacity) throws DbException,
	IOException, GeneralSecurityException {
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(messageLock) {
				synchronized(messageStatusLock) {
					Txn txn = db.startTransaction();
					try {
						capacity = Math.min(capacity, Batch.MAX_SIZE);
						Iterator<MessageId> it =
							db.getSendableMessages(txn, c, capacity).iterator();
						if(!it.hasNext()) {
							db.commitTransaction(txn);
							return null; // No more messages to send
						}
						BatchBuilder b = batchBuilderProvider.get();
						Set<MessageId> sent = new HashSet<MessageId>();
						while(it.hasNext()) {
							MessageId m = it.next();
							b.addMessage(db.getMessage(txn, m));
							sent.add(m);
						}
						Batch batch = b.build();
						// Record the contents of the batch
						assert !sent.isEmpty();
						db.addOutstandingBatch(txn, c, batch.getId(), sent);
						db.commitTransaction(txn);
						return batch;
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
		if(LOG.isLoggable(Level.FINE))
			LOG.fine("Received bundle from " + c + ", "
					+ b.getSize() + " bytes");
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
					// FIXME: Replace clearSubs and addSub with setSubs
					db.clearSubscriptions(txn, c);
					Set<GroupId> subs = h.getSubscriptions();
					for(GroupId sub : subs) db.addSubscription(txn, c, sub);
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
		b.close();
		retransmitLostBatches(c, h.getId());
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

	private void retransmitLostBatches(ContactId c, BundleId b)
	throws DbException {
		// Find any lost batches that need to be retransmitted
		Set<BatchId> lost;
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(messageLock) {
				synchronized(messageStatusLock) {
					Txn txn = db.startTransaction();
					try {
						lost = db.addReceivedBundle(txn, c, b);
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
