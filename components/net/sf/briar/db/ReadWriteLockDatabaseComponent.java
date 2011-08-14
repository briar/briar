package net.sf.briar.db;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.Rating;
import net.sf.briar.api.db.DatabaseListener.Event;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.NoSuchContactException;
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
	private final ReentrantReadWriteLock windowLock =
		new ReentrantReadWriteLock(true);

	@Inject
	ReadWriteLockDatabaseComponent(Database<Txn> db, DatabaseCleaner cleaner) {
		super(db, cleaner);
	}

	protected void expireMessages(int size) throws DbException {
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

	public void close() throws DbException {
		cleaner.stopCleaning();
		db.close();
	}

	public ContactId addContact(Map<String, Map<String, String>> transports,
			byte[] secret) throws DbException {
		if(LOG.isLoggable(Level.FINE)) LOG.fine("Adding contact");
		ContactId c;
		contactLock.writeLock().lock();
		try {
			transportLock.writeLock().lock();
			try {
				Txn txn = db.startTransaction();
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

	public void addLocallyGeneratedMessage(Message m) throws DbException {
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
						Txn txn = db.startTransaction();
						try {
							// Don't store the message if the user has
							// unsubscribed from the group or the message
							// predates the subscription
							if(db.containsSubscription(txn, m.getGroup(),
									m.getTimestamp())) {
								added = storeMessage(txn, m, null);
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

	public void findLostBatches(ContactId c) throws DbException {
		// Find any lost batches that need to be retransmitted
		Collection<BatchId> lost;
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			messageLock.readLock().lock();
			try {
				messageStatusLock.writeLock().lock();
				try {
					Txn txn = db.startTransaction();
					try {
						lost = db.getLostBatches(txn, c);
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

	public void generateAck(ContactId c, AckWriter a) throws DbException,
	IOException {
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			messageStatusLock.writeLock().lock();
			try {
				Txn txn = db.startTransaction();
				try {
					Collection<BatchId> acks = db.getBatchesToAck(txn, c);
					Collection<BatchId> sent = new ArrayList<BatchId>();
					for(BatchId b : acks) if(a.writeBatchId(b)) sent.add(b);
					a.finish();
					db.removeBatchesToAck(txn, c, sent);
					if(LOG.isLoggable(Level.FINE))
						LOG.fine("Added " + acks.size() + " acks");
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				} catch(IOException e) {
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

	public void generateBatch(ContactId c, BatchWriter b) throws DbException,
	IOException {
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			messageLock.readLock().lock();
			try {
				Collection<MessageId> sent;
				int bytesSent = 0;
				messageStatusLock.readLock().lock();
				try {
					subscriptionLock.readLock().lock();
					try {
						Txn txn = db.startTransaction();
						try {
							sent = new ArrayList<MessageId>();
							int capacity = b.getCapacity();
							Collection<MessageId> sendable =
								db.getSendableMessages(txn, c, capacity);
							for(MessageId m : sendable) {
								byte[] raw = db.getMessage(txn, m);
								if(!b.writeMessage(raw)) break;
								bytesSent += raw.length;
								sent.add(m);
							}
							db.commitTransaction(txn);
						} catch(DbException e) {
							db.abortTransaction(txn);
							throw e;
						} catch(IOException e) {
							db.abortTransaction(txn);
							throw e;
						}
					} finally {
						subscriptionLock.readLock().unlock();
					}
				} finally {
					messageStatusLock.readLock().unlock();
				}
				// Record the contents of the batch, unless it's empty
				if(sent.isEmpty()) return;
				BatchId id = b.finish();
				messageStatusLock.writeLock().lock();
				try {
					Txn txn = db.startTransaction();
					try {
						db.addOutstandingBatch(txn, c, id, sent);
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

	public Collection<MessageId> generateBatch(ContactId c, BatchWriter b,
			Collection<MessageId> requested) throws DbException, IOException {
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			messageLock.readLock().lock();
			try {
				Collection<MessageId> sent, considered;
				messageStatusLock.readLock().lock();
				try{
					subscriptionLock.readLock().lock();
					try {
						Txn txn = db.startTransaction();
						try {
							sent = new ArrayList<MessageId>();
							considered = new ArrayList<MessageId>();
							int bytesSent = 0;
							for(MessageId m : requested) {
								byte[] raw = db.getMessageIfSendable(txn, c, m);
								// If the message is still sendable, try to add
								// it to the batch. If the batch is full, don't
								// treat the message as considered, and don't
								// try to add any further messages.
								if(raw != null) {
									if(!b.writeMessage(raw)) break;
									bytesSent += raw.length;
									sent.add(m);
								}
								considered.add(m);
							}
							db.commitTransaction(txn);
						} catch(DbException e) {
							db.abortTransaction(txn);
							throw e;
						} catch(IOException e) {
							db.abortTransaction(txn);
							throw e;
						}
					} finally {
						subscriptionLock.readLock().unlock();
					}
				} finally {
					messageStatusLock.readLock().unlock();
				}
				// Record the contents of the batch, unless it's empty
				if(sent.isEmpty()) return considered;
				BatchId id = b.finish();
				messageStatusLock.writeLock().lock();
				try {
					Txn txn = db.startTransaction();
					try {
						db.addOutstandingBatch(txn, c, id, sent);
						db.commitTransaction(txn);
						return considered;
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

	public Collection<MessageId> generateOffer(ContactId c, OfferWriter o)
	throws DbException, IOException {
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			messageLock.readLock().lock();
			try {
				messageStatusLock.readLock().lock();
				try {
					Txn txn = db.startTransaction();
					try {
						Collection<MessageId> sendable =
							db.getSendableMessages(txn, c, Integer.MAX_VALUE);
						Iterator<MessageId> it = sendable.iterator();
						Collection<MessageId> sent = new ArrayList<MessageId>();
						while(it.hasNext()) {
							MessageId m = it.next();
							if(!o.writeMessageId(m)) break;
							sent.add(m);
						}
						o.finish();
						db.commitTransaction(txn);
						return sent;
					} catch(DbException e) {
						db.abortTransaction(txn);
						throw e;
					} catch(IOException e) {
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
	}

	public void generateSubscriptionUpdate(ContactId c, SubscriptionWriter s)
	throws DbException, IOException {
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			subscriptionLock.readLock().lock();
			try {
				Txn txn = db.startTransaction();
				try {
					Map<Group, Long> subs = db.getVisibleSubscriptions(txn, c);
					s.writeSubscriptions(subs, System.currentTimeMillis());
					if(LOG.isLoggable(Level.FINE))
						LOG.fine("Added " + subs.size() + " subscriptions");
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				} catch(IOException e) {
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

	public void generateTransportUpdate(ContactId c, TransportWriter t)
	throws DbException, IOException {
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			transportLock.readLock().lock();
			try {
				Txn txn = db.startTransaction();
				try {
					Map<String, Map<String, String>> transports =
						db.getTransports(txn);
					t.writeTransports(transports, System.currentTimeMillis());
					if(LOG.isLoggable(Level.FINE))
						LOG.fine("Added " + transports.size() + " transports");
					db.commitTransaction(txn);
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				} catch(IOException e) {
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

	public ConnectionWindow getConnectionWindow(ContactId c, int transportId)
	throws DbException {
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			windowLock.readLock().lock();
			try {
				Txn txn = db.startTransaction();
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
			Txn txn = db.startTransaction();
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

	public byte[] getSharedSecret(ContactId c) throws DbException {
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			Txn txn = db.startTransaction();
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
			Txn txn = db.startTransaction();
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
			Txn txn = db.startTransaction();
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
			Txn txn = db.startTransaction();
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
				Txn txn = db.startTransaction();
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
				Txn txn = db.startTransaction();
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
						Txn txn = db.startTransaction();
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
		// Mark all messages in acked batches as seen
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			messageLock.readLock().lock();
			try {
				messageStatusLock.writeLock().lock();
				try {
					Collection<BatchId> acks = a.getBatchIds();
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
						Txn txn = db.startTransaction();
						try {
							int received = 0, stored = 0;
							for(Message m : b.getMessages()) {
								received++;
								if(db.containsVisibleSubscription(txn,
										m.getGroup(), c, m.getTimestamp())) {
									if(storeMessage(txn, m, c)) {
										anyAdded = true;
										stored++;
									}
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
		// Call the listeners outside the lock
		if(anyAdded) callListeners(Event.MESSAGES_ADDED);
	}

	public void receiveOffer(ContactId c, Offer o, RequestWriter r)
	throws DbException, IOException {
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			messageLock.readLock().lock();
			try {
				messageStatusLock.writeLock().lock();
				try {
					subscriptionLock.readLock().lock();
					try {
						Collection<MessageId> offered = o.getMessageIds();
						BitSet request = new BitSet(offered.size());
						Txn txn = db.startTransaction();
						try {
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
						r.writeRequest(o.getId(), request, offered.size());
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

	public void receiveSubscriptionUpdate(ContactId c, SubscriptionUpdate s)
	throws DbException {
		// Update the contact's subscriptions
		contactLock.readLock().lock();
		try {
			if(!containsContact(c)) throw new NoSuchContactException();
			subscriptionLock.writeLock().lock();
			try {
				Txn txn = db.startTransaction();
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
				Txn txn = db.startTransaction();
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
				Txn txn = db.startTransaction();
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

	public void setTransportConfig(String name,
			Map<String, String> config) throws DbException {
		boolean changed = false;
		transportLock.writeLock().lock();
		try {
			Txn txn = db.startTransaction();
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
			Txn txn = db.startTransaction();
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
				Txn txn = db.startTransaction();
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
			Txn txn = db.startTransaction();
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
						Txn txn = db.startTransaction();
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
}