package net.sf.briar.db;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.Rating;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.NoSuchContactException;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.BundleId;
import net.sf.briar.api.protocol.BundleReader;
import net.sf.briar.api.protocol.BundleWriter;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Header;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageId;

import com.google.inject.Inject;

/**
 * An implementation of DatabaseComponent using reentrant read-write locks.
 * This implementation can allow writers to starve.
 */
class ReadWriteLockDatabaseComponent<Txn> extends DatabaseComponentImpl<Txn> {

	private static final Logger LOG =
		Logger.getLogger(ReadWriteLockDatabaseComponent.class.getName());

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

	@Inject
	ReadWriteLockDatabaseComponent(Database<Txn> db, DatabaseCleaner cleaner) {
		super(db, cleaner);
	}

	public void close() throws DbException {
		cleaner.stopCleaning();
		contactLock.writeLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				messageStatusLock.writeLock().lock();
				try {
					ratingLock.writeLock().lock();
					try {
						subscriptionLock.writeLock().lock();
						try {
							db.close();
						} finally {
							subscriptionLock.writeLock().unlock();
						}
					} finally {
						ratingLock.writeLock().unlock();
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
	}

	public ContactId addContact(Map<String, String> transports)
	throws DbException {
		if(LOG.isLoggable(Level.FINE)) LOG.fine("Adding contact");
		contactLock.writeLock().lock();
		try {
			transportLock.writeLock().lock();
			try {
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
			} finally {
				transportLock.writeLock().unlock();
			}
		} finally {
			contactLock.writeLock().unlock();
		}
	}

	public void addLocallyGeneratedMessage(Message m) throws DbException {
		waitForPermissionToWrite();
		contactLock.readLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				messageStatusLock.writeLock().lock();
				try {
					subscriptionLock.readLock().lock();
					try {
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
	}

	protected void expireMessages(long size) throws DbException {
		contactLock.readLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				messageStatusLock.writeLock().lock();
				try {
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

	public void generateBundle(ContactId c, BundleWriter b) throws DbException,
	IOException, GeneralSecurityException {
		if(LOG.isLoggable(Level.FINE)) LOG.fine("Generating bundle for " + c);
		Set<BatchId> acks;
		Set<GroupId> subs;
		Map<String, String> transports;
		// Add acks
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			messageStatusLock.writeLock().lock();
			try {
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
			} finally {
				messageStatusLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		// Add subscriptions
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			subscriptionLock.readLock().lock();
			try {
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
			} finally {
				subscriptionLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		// Add transport details
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			transportLock.readLock().lock();
			try {
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
			} finally {
				transportLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
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
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			messageLock.readLock().lock();
			try {
				Set<MessageId> sent;
				int bytesSent = 0;
				BatchId batchId;
				messageStatusLock.readLock().lock();
				try {
					Txn txn = db.startTransaction();
					try {
						long capacity = Math.min(b.getRemainingCapacity(),
								Batch.MAX_SIZE);
						Iterator<MessageId> it =
							db.getSendableMessages(txn, c, capacity).iterator();
						if(!it.hasNext()) {
							db.commitTransaction(txn);
							return false; // No more messages to send
						}
						sent = new HashSet<MessageId>();
						List<Message> messages = new ArrayList<Message>();
						while(it.hasNext()) {
							MessageId m = it.next();
							Message message = db.getMessage(txn, m);
							bytesSent += message.getSize();
							messages.add(message);
							sent.add(m);
						}
						batchId = b.addBatch(messages);
						db.commitTransaction(txn);
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					} catch(IOException e) {
						db.abortTransaction(txn);
						throw e;
					} catch(GeneralSecurityException e) {
						db.abortTransaction(txn);
						throw e;
					}
				} finally {
					messageStatusLock.readLock().unlock();
				}
				// Record the contents of the batch
				messageStatusLock.writeLock().lock();
				try {
					Txn txn = db.startTransaction();
					try {
						assert !sent.isEmpty();
						db.addOutstandingBatch(txn, c, batchId, sent);
						db.commitTransaction(txn);
						// Don't create another batch if this one was half-empty
						return bytesSent > Batch.MAX_SIZE / 2;
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

	public Set<ContactId> getContacts() throws DbException {
		contactLock.readLock().lock();
		try {
			Txn txn = db.startTransaction();
			try {
				Set<ContactId> contacts = db.getContacts(txn);
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
			Txn txn = db.startTransaction();
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

	public Set<GroupId> getSubscriptions() throws DbException {
		subscriptionLock.readLock().lock();
		try {
			Txn txn = db.startTransaction();
			try {
				Set<GroupId> subs = db.getSubscriptions(txn);
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

	public Map<String, String> getTransports() throws DbException {
		transportLock.readLock().lock();
		try {
			Txn txn = db.startTransaction();
			try {
				Map<String, String> transports = db.getTransports(txn);
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

	public Map<String, String> getTransports(ContactId c) throws DbException {
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			transportLock.readLock().lock();
			try {
				Txn txn = db.startTransaction();
				try {
					Map<String, String> transports = db.getTransports(txn, c);
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

	public void receiveBundle(ContactId c, BundleReader b) throws DbException,
	IOException, GeneralSecurityException {
		if(LOG.isLoggable(Level.FINE)) LOG.fine("Received bundle from " + c);
		Header h;
		// Mark all messages in acked batches as seen
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			h = b.getHeader();
			messageLock.readLock().lock();
			try {
				messageStatusLock.writeLock().lock();
				try {
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
				} finally {
					messageStatusLock.writeLock().unlock();
				}
			} finally {
				messageLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		// Update the contact's subscriptions
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			subscriptionLock.writeLock().lock();
			try {
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
			} finally {
				subscriptionLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
		// Update the contact's transport details
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			transportLock.writeLock().lock();
			try {
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
			} finally {
				transportLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
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
		retransmitLostBatches(c, h.getId());
		System.gc();
	}

	private void storeBatch(ContactId c, Batch b) throws DbException {
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
	}

	private void retransmitLostBatches(ContactId c, BundleId b)
	throws DbException {
		// Find any lost batches that need to be retransmitted
		Set<BatchId> lost;
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			messageLock.readLock().lock();
			try {
				messageStatusLock.writeLock().lock();
				try {
					Txn txn = db.startTransaction();
					try {
						lost = db.addReceivedBundle(txn, c, b);
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
		for(BatchId batch : lost) {
			contactLock.readLock().lock();
			try {
				if(!containsContact(c)) throw new NoSuchContactException();
				messageLock.readLock().lock();
				try {
					messageStatusLock.writeLock().lock();
					try {
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
	}

	public void removeContact(ContactId c) throws DbException {
		if(LOG.isLoggable(Level.FINE)) LOG.fine("Removing contact " + c);
		contactLock.writeLock().lock();
		try {
			messageStatusLock.writeLock().lock();
			try {
				subscriptionLock.writeLock().lock();
				try {
					transportLock.writeLock().lock();
					try {
						Txn txn = db.startTransaction();
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
			contactLock.writeLock().unlock();
		}
	}

	public void setRating(AuthorId a, Rating r) throws DbException {
		messageLock.writeLock().lock();
		try {
			ratingLock.writeLock().lock();
			try {
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
			} finally {
				ratingLock.writeLock().unlock();
			}
		} finally {
			messageLock.writeLock().unlock();
		}
	}

	public void setTransports(ContactId c, Map<String, String> transports)
	throws DbException {
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			transportLock.writeLock().lock();
			try {
				Txn txn = db.startTransaction();
				try {
					db.setTransports(txn, c, transports);
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

	public void subscribe(GroupId g) throws DbException {
		if(LOG.isLoggable(Level.FINE)) LOG.fine("Subscribing to " + g);
		subscriptionLock.writeLock().lock();
		try {
			Txn txn = db.startTransaction();
			try {
				db.addSubscription(txn, g);
				db.commitTransaction(txn);
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			subscriptionLock.writeLock().unlock();
		}
	}

	public void unsubscribe(GroupId g) throws DbException {
		if(LOG.isLoggable(Level.FINE)) LOG.fine("Unsubscribing from " + g);
		contactLock.readLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				messageStatusLock.writeLock().lock();
				try {
					subscriptionLock.writeLock().lock();
					try {
						Txn txn = db.startTransaction();
						try {
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
					messageStatusLock.writeLock().unlock();
				}
			} finally {
				messageLock.writeLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}
}