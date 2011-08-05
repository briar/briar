package net.sf.briar.db;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.Rating;
import net.sf.briar.api.db.DatabaseListener;
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

	protected void expireMessages(int size) throws DbException {
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

	public ContactId addContact(Map<String, Map<String, String>> transports)
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
		boolean added = false;
		waitForPermissionToWrite();
		synchronized(contactLock) {
			synchronized(messageLock) {
				synchronized(messageStatusLock) {
					synchronized(subscriptionLock) {
						Txn txn = db.startTransaction();
						try {
							// Don't store the message if the user has
							// unsubscribed from the group or the message
							// predates the subscription
							if(db.containsSubscription(txn, m.getGroup(),
									m.getTimestamp())) {
								added = storeMessage(txn, m, null);
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
		// Call the listeners outside the lock
		if(added) callListeners(DatabaseListener.Event.MESSAGES_ADDED);
	}

	public void findLostBatches(ContactId c) throws DbException {
		// Find any lost batches that need to be retransmitted
		Collection<BatchId> lost;
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

	public void generateAck(ContactId c, AckWriter a) throws DbException,
	IOException {
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(messageStatusLock) {
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
			}
		}
	}

	public void generateBatch(ContactId c, BatchWriter b) throws DbException,
	IOException {
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(messageLock) {
				synchronized(messageStatusLock) {
					synchronized(subscriptionLock) {
						Txn txn = db.startTransaction();
						try {
							Collection<MessageId> sent =
								new ArrayList<MessageId>();
							int bytesSent = 0;
							int capacity = b.getCapacity();
							Collection<MessageId> sendable =
								db.getSendableMessages(txn, c, capacity);
							for(MessageId m : sendable) {
								byte[] raw = db.getMessage(txn, m);
								if(!b.writeMessage(raw)) break;
								bytesSent += raw.length;
								sent.add(m);
							}
							// If the batch is not empty, calculate its ID and
							// record it as outstanding
							if(!sent.isEmpty()) {
								BatchId id = b.finish();
								db.addOutstandingBatch(txn, c, id, sent);
							}
							db.commitTransaction(txn);
						} catch(DbException e) {
							db.abortTransaction(txn);
							throw e;
						} catch(IOException e) {
							db.abortTransaction(txn);
							throw e;
						}
					}
				}
			}
		}
	}

	public Collection<MessageId> generateBatch(ContactId c, BatchWriter b,
			Collection<MessageId> requested) throws DbException, IOException {
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(messageLock) {
				synchronized(messageStatusLock) {
					synchronized(subscriptionLock) {
						Txn txn = db.startTransaction();
						try {
							Collection<MessageId> sent =
								new ArrayList<MessageId>();
							Collection<MessageId> considered =
								new ArrayList<MessageId>();
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
							// If the batch is not empty, calculate its ID and
							// record it as outstanding
							if(!sent.isEmpty()) {
								BatchId id = b.finish();
								db.addOutstandingBatch(txn, c, id, sent);
							}
							db.commitTransaction(txn);
							return considered;
						} catch(DbException e) {
							db.abortTransaction(txn);
							throw e;
						} catch(IOException e) {
							db.abortTransaction(txn);
							throw e;
						}
					}
				}
			}
		}
	}

	public Collection<MessageId> generateOffer(ContactId c, OfferWriter o)
	throws DbException, IOException {
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(messageLock) {
				synchronized(messageStatusLock) {
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
				}
			}
		}
	}

	public void generateSubscriptionUpdate(ContactId c, SubscriptionWriter s)
	throws DbException, IOException {
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(subscriptionLock) {
				Txn txn = db.startTransaction();
				try {
					Map<Group, Long> subs = db.getVisibleSubscriptions(txn, c);
					s.writeSubscriptions(subs);
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
			}
		}
	}

	public void generateTransportUpdate(ContactId c, TransportWriter t)
	throws DbException, IOException {
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(transportLock) {
				Txn txn = db.startTransaction();
				try {
					Map<String, Map<String, String>> transports =
						db.getTransports(txn);
					t.writeTransports(transports);
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
			}
		}
	}

	public Collection<ContactId> getContacts() throws DbException {
		synchronized(contactLock) {
			Txn txn = db.startTransaction();
			try {
				Collection<ContactId> contacts = db.getContacts(txn);
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

	public Collection<Group> getSubscriptions() throws DbException {
		synchronized(subscriptionLock) {
			Txn txn = db.startTransaction();
			try {
				Collection<Group> subs = db.getSubscriptions(txn);
				db.commitTransaction(txn);
				return subs;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		}
	}

	public Map<String, String> getTransportConfig(String name)
	throws DbException {
		synchronized(transportLock) {
			Txn txn = db.startTransaction();
			try {
				Map<String, String> config = db.getTransportConfig(txn, name);
				db.commitTransaction(txn);
				return config;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		}
	}

	public Map<String, Map<String, String>> getTransports() throws DbException {
		synchronized(transportLock) {
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
		}
	}

	public Map<String, Map<String, String>> getTransports(ContactId c)
	throws DbException {
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(transportLock) {
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
			}
		}
	}

	public Collection<ContactId> getVisibility(GroupId g) throws DbException {
		synchronized(contactLock) {
			synchronized(subscriptionLock) {
				Txn txn = db.startTransaction();
				try {
					Collection<ContactId> visible = db.getVisibility(txn, g);
					db.commitTransaction(txn);
					return visible;
				} catch(DbException e) {
					db.abortTransaction(txn);
					throw e;
				}
			}
		}
	}

	public boolean hasSendableMessages(ContactId c) throws DbException {
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(messageLock) {
				synchronized(messageStatusLock) {
					synchronized(subscriptionLock) {
						Txn txn = db.startTransaction();
						try {
							boolean has = db.hasSendableMessages(txn, c);
							db.commitTransaction(txn);
							return has;
						} catch(DbException e) {
							db.abortTransaction(txn);
							throw e;
						}
					}
				}
			}
		}
	}

	public void receiveAck(ContactId c, Ack a) throws DbException {
		// Mark all messages in acked batches as seen
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(messageLock) {
				synchronized(messageStatusLock) {
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
				}
			}
		}
	}

	public void receiveBatch(ContactId c, Batch b) throws DbException {
		boolean anyAdded = false;
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
								if(db.containsVisibleSubscription(txn, g, c,
										m.getTimestamp())) {
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
					}
				}
			}
		}
		// Call the listeners outside the lock
		if(anyAdded) callListeners(DatabaseListener.Event.MESSAGES_ADDED);
	}

	public void receiveOffer(ContactId c, Offer o, RequestWriter r)
	throws DbException, IOException {
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(messageLock) {
				synchronized(messageStatusLock) {
					synchronized(subscriptionLock) {
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
						r.writeBitmap(request, offered.size());
					}
				}
			}
		}
	}

	public void receiveSubscriptionUpdate(ContactId c, SubscriptionUpdate s)
	throws DbException {
		// Update the contact's subscriptions
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(subscriptionLock) {
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
			}
		}
	}

	public void receiveTransportUpdate(ContactId c, TransportUpdate t)
	throws DbException {
		// Update the contact's transport properties
		synchronized(contactLock) {
			if(!containsContact(c)) throw new NoSuchContactException();
			synchronized(transportLock) {
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

	public void setTransportConfig(String name,
			Map<String, String> config) throws DbException {
		boolean changed = false;
		synchronized(transportLock) {
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
		}
		// Call the listeners outside the lock
		if(changed) callListeners(DatabaseListener.Event.TRANSPORTS_UPDATED);
	}

	public void setTransportProperties(String name,
			Map<String, String> properties) throws DbException {
		boolean changed = false;
		synchronized(transportLock) {
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
		}
		// Call the listeners outside the lock
		if(changed) callListeners(DatabaseListener.Event.TRANSPORTS_UPDATED);
	}

	public void setVisibility(GroupId g, Collection<ContactId> visible)
	throws DbException {
		synchronized(contactLock) {
			synchronized(subscriptionLock) {
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
			}
		}
	}

	public void subscribe(Group g) throws DbException {
		if(LOG.isLoggable(Level.FINE)) LOG.fine("Subscribing to " + g);
		boolean added = false;
		synchronized(subscriptionLock) {
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
		}
		// Call the listeners outside the lock
		if(added) callListeners(DatabaseListener.Event.SUBSCRIPTIONS_UPDATED);
	}

	public void unsubscribe(GroupId g) throws DbException {
		if(LOG.isLoggable(Level.FINE)) LOG.fine("Unsubscribing from " + g);
		boolean removed = false;
		synchronized(contactLock) {
			synchronized(messageLock) {
				synchronized(messageStatusLock) {
					synchronized(subscriptionLock) {
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
					}
				}
			}
		}
		// Call the listeners outside the lock
		if(removed) callListeners(DatabaseListener.Event.SUBSCRIPTIONS_UPDATED);
	}
}
