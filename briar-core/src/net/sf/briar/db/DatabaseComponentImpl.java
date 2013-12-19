package net.sf.briar.db;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.db.DatabaseConstants.BYTES_PER_SWEEP;
import static net.sf.briar.db.DatabaseConstants.CRITICAL_FREE_SPACE;
import static net.sf.briar.db.DatabaseConstants.MAX_BYTES_BETWEEN_SPACE_CHECKS;
import static net.sf.briar.db.DatabaseConstants.MAX_MS_BETWEEN_SPACE_CHECKS;
import static net.sf.briar.db.DatabaseConstants.MIN_FREE_SPACE;

import java.io.IOException;
import java.util.ArrayList;
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

import javax.inject.Inject;

import net.sf.briar.api.Author;
import net.sf.briar.api.AuthorId;
import net.sf.briar.api.Contact;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.LocalAuthor;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.db.AckAndRequest;
import net.sf.briar.api.db.ContactExistsException;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.LocalAuthorExistsException;
import net.sf.briar.api.db.MessageHeader;
import net.sf.briar.api.db.NoSuchContactException;
import net.sf.briar.api.db.NoSuchLocalAuthorException;
import net.sf.briar.api.db.NoSuchMessageException;
import net.sf.briar.api.db.NoSuchSubscriptionException;
import net.sf.briar.api.db.NoSuchTransportException;
import net.sf.briar.api.db.event.ContactAddedEvent;
import net.sf.briar.api.db.event.ContactRemovedEvent;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.LocalAuthorAddedEvent;
import net.sf.briar.api.db.event.LocalAuthorRemovedEvent;
import net.sf.briar.api.db.event.LocalSubscriptionsUpdatedEvent;
import net.sf.briar.api.db.event.LocalTransportsUpdatedEvent;
import net.sf.briar.api.db.event.MessageAddedEvent;
import net.sf.briar.api.db.event.MessageExpiredEvent;
import net.sf.briar.api.db.event.MessageReceivedEvent;
import net.sf.briar.api.db.event.RemoteRetentionTimeUpdatedEvent;
import net.sf.briar.api.db.event.RemoteSubscriptionsUpdatedEvent;
import net.sf.briar.api.db.event.RemoteTransportsUpdatedEvent;
import net.sf.briar.api.db.event.SubscriptionAddedEvent;
import net.sf.briar.api.db.event.SubscriptionRemovedEvent;
import net.sf.briar.api.db.event.TransportAddedEvent;
import net.sf.briar.api.db.event.TransportRemovedEvent;
import net.sf.briar.api.lifecycle.ShutdownManager;
import net.sf.briar.api.messaging.Ack;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.messaging.GroupStatus;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageId;
import net.sf.briar.api.messaging.Offer;
import net.sf.briar.api.messaging.Request;
import net.sf.briar.api.messaging.RetentionAck;
import net.sf.briar.api.messaging.RetentionUpdate;
import net.sf.briar.api.messaging.SubscriptionAck;
import net.sf.briar.api.messaging.SubscriptionUpdate;
import net.sf.briar.api.messaging.TransportAck;
import net.sf.briar.api.messaging.TransportUpdate;
import net.sf.briar.api.transport.Endpoint;
import net.sf.briar.api.transport.TemporarySecret;

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
	private final ReentrantReadWriteLock identityLock =
			new ReentrantReadWriteLock(true);
	private final ReentrantReadWriteLock messageLock =
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

	public boolean open() throws DbException, IOException {
		synchronized(openCloseLock) {
			if(open) throw new IllegalStateException();
			open = true;
			boolean reopened = db.open();
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
			return reopened;
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

	public ContactId addContact(Author remote, AuthorId local)
			throws DbException {
		ContactId c;
		contactLock.writeLock().lock();
		try {
			identityLock.readLock().lock();
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
										if(db.containsContact(txn, remote.getId()))
											throw new ContactExistsException();
										if(!db.containsLocalAuthor(txn, local))
											throw new NoSuchLocalAuthorException();
										c = db.addContact(txn, remote, local);
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
				identityLock.readLock().unlock();
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

	public boolean addGroup(Group g) throws DbException {
		boolean added = false;
		messageLock.writeLock().lock();
		try {
			subscriptionLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsGroup(txn, g.getId()))
						added = db.addGroup(txn, g);
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
		if(added) callListeners(new SubscriptionAddedEvent(g));
		return added;
	}

	public void addLocalAuthor(LocalAuthor a) throws DbException {
		contactLock.writeLock().lock();
		try {
			identityLock.writeLock().lock();
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
										if(db.containsLocalAuthor(txn, a.getId()))
											throw new LocalAuthorExistsException();
										db.addLocalAuthor(txn, a);
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
				identityLock.writeLock().unlock();
			}
		} finally {
			contactLock.writeLock().unlock();
		}
		callListeners(new LocalAuthorAddedEvent(a.getId()));
	}

	public void addLocalMessage(Message m) throws DbException {
		boolean duplicate;
		contactLock.readLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				subscriptionLock.readLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						duplicate = db.containsMessage(txn, m.getId());
						if(!duplicate) {
							GroupId g = m.getGroup().getId();
							if(db.containsGroup(txn, g))
								addMessage(txn, m, null);
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
		if(!duplicate)
			callListeners(new MessageAddedEvent(m.getGroup(), null));
	}

	/**
	 * Stores the given message, marks it as read if it was locally generated,
	 * otherwise marks it as seen by the sender, and marks it as unseen by all
	 * other contacts.
	 * <p>
	 * Locking: contact read, message write, subscription read.
	 * @param sender null for a locally generated message.
	 */
	private void addMessage(T txn, Message m, ContactId sender)
			throws DbException {
		db.addMessage(txn, m, sender != null);
		MessageId id = m.getId();
		if(sender == null) db.setReadFlag(txn, id, true);
		else db.addStatus(txn, sender, id, true);
		for(ContactId c : db.getContactIds(txn))
			if(!c.equals(sender)) db.addStatus(txn, c, id, false);
		// Count the bytes stored
		synchronized(spaceLock) {
			bytesStoredSinceLastCheck += m.getSerialised().length;
		}
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

	public boolean addTransport(TransportId t, long maxLatency)
			throws DbException {
		boolean added;
		transportLock.writeLock().lock();
		try {
			windowLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					added = db.addTransport(txn, t, maxLatency);
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
		if(added) callListeners(new TransportAddedEvent(t, maxLatency));
		return added;
	}

	public boolean containsSendableMessages(ContactId c) throws DbException {
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
						boolean has = db.containsSendableMessages(txn, c);
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
		if(offered.isEmpty()) return null;
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

	public Collection<GroupStatus> getAvailableGroups() throws DbException {
		subscriptionLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Collection<GroupStatus> groups = db.getAvailableGroups(txn);
				db.commitTransaction(txn);
				return groups;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			subscriptionLock.readLock().unlock();
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
				if(!db.containsGroup(txn, g))
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

	public Collection<Group> getGroups() throws DbException {
		subscriptionLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Collection<Group> groups = db.getGroups(txn);
				db.commitTransaction(txn);
				return groups;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			subscriptionLock.readLock().unlock();
		}
	}

	public GroupId getInboxGroup(ContactId c) throws DbException {
		contactLock.readLock().lock();
		try {
			subscriptionLock.readLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					GroupId inbox = db.getInboxGroup(txn, c);
					db.commitTransaction(txn);
					return inbox;
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

	public Collection<MessageHeader> getInboxMessageHeaders(ContactId c)
			throws DbException {
		contactLock.readLock().lock();
		try {
			identityLock.readLock().lock();
			try {
				messageLock.readLock().lock();
				try {
					subscriptionLock.readLock().lock();
					try {
						T txn = db.startTransaction();
						try {
							if(!db.containsContact(txn, c))
								throw new NoSuchContactException();
							Collection<MessageHeader> headers =
									db.getInboxMessageHeaders(txn, c);
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
					messageLock.readLock().unlock();
				}
			} finally {
				identityLock.readLock().unlock();
			}
		} finally {
			contactLock.readLock().unlock();
		}
	}

	public Map<ContactId, Long> getLastConnected() throws DbException {
		windowLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Map<ContactId, Long> times = db.getLastConnected(txn);
				db.commitTransaction(txn);
				return times;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			windowLock.readLock().unlock();
		}
	}

	public LocalAuthor getLocalAuthor(AuthorId a) throws DbException {
		identityLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if(!db.containsLocalAuthor(txn, a))
					throw new NoSuchLocalAuthorException();
				LocalAuthor localAuthor = db.getLocalAuthor(txn, a);
				db.commitTransaction(txn);
				return localAuthor;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			identityLock.readLock().unlock();
		}
	}

	public Collection<LocalAuthor> getLocalAuthors() throws DbException {
		identityLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Collection<LocalAuthor> authors = db.getLocalAuthors(txn);
				db.commitTransaction(txn);
				return authors;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			identityLock.readLock().unlock();
		}
	}

	public Map<TransportId, TransportProperties> getLocalProperties()
			throws DbException {
		transportLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Map<TransportId, TransportProperties> properties =
						db.getLocalProperties(txn);
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

	public Collection<MessageHeader> getMessageHeaders(GroupId g)
			throws DbException {
		messageLock.readLock().lock();
		try {
			subscriptionLock.readLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsGroup(txn, g))
						throw new NoSuchSubscriptionException();
					Collection<MessageHeader> headers =
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
			messageLock.readLock().unlock();
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

	public Map<TransportId, Long> getTransportLatencies() throws DbException {
		transportLock.readLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				Map<TransportId, Long> latencies =
						db.getTransportLatencies(txn);
				db.commitTransaction(txn);
				return latencies;
			} catch(DbException e) {
				db.abortTransaction(txn);
				throw e;
			}
		} finally {
			transportLock.readLock().unlock();
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
				if(!db.containsGroup(txn, g))
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
						if(counter != -1) {
							long now = clock.currentTimeMillis();
							db.setLastConnected(txn, c, now);
						}
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
					for(MessageId m : a.getMessageIds())
						db.setStatusSeenIfVisible(txn, c, m);
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
		boolean duplicate, visible;
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
						duplicate = db.containsMessage(txn, m.getId());
						GroupId g = m.getGroup().getId();
						visible = db.containsVisibleGroup(txn, c, g);
						if(!duplicate && visible) addMessage(txn, m, c);
						if(visible) db.addMessageToAck(txn, c, m.getId());
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
		if(visible) callListeners(new MessageReceivedEvent(c));
		if(!duplicate) callListeners(new MessageAddedEvent(m.getGroup(), c));
	}

	public AckAndRequest receiveOffer(ContactId c, Offer o) throws DbException {
		List<MessageId> ack = new ArrayList<MessageId>();
		List<MessageId> request = new ArrayList<MessageId>();
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
						for(MessageId m : o.getMessageIds()) {
							// If the message is present and visible, ack it
							if(db.setStatusSeenIfVisible(txn, c, m)) ack.add(m);
							else request.add(m);
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
		Ack a = ack.isEmpty() ? null : new Ack(ack);
		Request r = request.isEmpty() ? null : new Request(request);
		return new AckAndRequest(a, r);
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
		boolean updated;
		contactLock.readLock().lock();
		try {
			retentionLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					updated = db.setRetentionTime(txn, c, u.getRetentionTime(),
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
		if(updated) callListeners(new RemoteRetentionTimeUpdatedEvent(c));
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
		boolean updated;
		contactLock.readLock().lock();
		try {
			subscriptionLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					Collection<Group> groups = u.getGroups();
					long version = u.getVersion();
					updated = db.setGroups(txn, c, groups, version);
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
		if(updated) callListeners(new RemoteSubscriptionsUpdatedEvent(c));
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
		boolean updated;
		contactLock.readLock().lock();
		try {
			transportLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					TransportId t = u.getId();
					TransportProperties p = u.getProperties();
					long version = u.getVersion();
					updated = db.setRemoteProperties(txn, c, t, p, version);
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
		if(updated)
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

	public void removeGroup(Group g) throws DbException {
		Collection<ContactId> affected;
		messageLock.writeLock().lock();
		try {
			subscriptionLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					GroupId id = g.getId();
					if(!db.containsGroup(txn, id))
						throw new NoSuchSubscriptionException();
					affected = db.getVisibility(txn, id);
					db.removeGroup(txn, id);
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

	public void removeLocalAuthor(AuthorId a) throws DbException {
		Collection<ContactId> affected;
		contactLock.writeLock().lock();
		try {
			identityLock.writeLock().lock();
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
										if(!db.containsLocalAuthor(txn, a))
											throw new NoSuchLocalAuthorException();
										affected = db.getContacts(txn, a);
										db.removeLocalAuthor(txn, a);
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
				identityLock.writeLock().unlock();
			}
		} finally {
			contactLock.writeLock().unlock();
		}
		for(ContactId c : affected) callListeners(new ContactRemovedEvent(c));
		callListeners(new LocalAuthorRemovedEvent(a));
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

	public void setInboxGroup(ContactId c, Group g) throws DbException {
		if(!g.isPrivate()) throw new IllegalArgumentException();
		contactLock.readLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				subscriptionLock.writeLock().lock();
				try {
					T txn = db.startTransaction();
					try {
						if(!db.containsContact(txn, c))
							throw new NoSuchContactException();
						db.setInboxGroup(txn, c, g);
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
			contactLock.readLock().unlock();
		}
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

	public void setRemoteProperties(ContactId c,
			Map<TransportId, TransportProperties> p) throws DbException {
		contactLock.readLock().lock();
		try {
			transportLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					db.setRemoteProperties(txn, c, p);
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

	public void setVisibility(GroupId g, Collection<ContactId> visible)
			throws DbException {
		Collection<ContactId> affected = new ArrayList<ContactId>();
		contactLock.readLock().lock();
		try {
			subscriptionLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsGroup(txn, g))
						throw new NoSuchSubscriptionException();
					// Use HashSets for O(1) lookups, O(n) overall running time
					HashSet<ContactId> now = new HashSet<ContactId>(visible);
					Collection<ContactId> before = db.getVisibility(txn, g);
					before = new HashSet<ContactId>(before);
					// Set the group's visibility for each current contact
					for(ContactId c : db.getContactIds(txn)) {
						boolean wasBefore = before.contains(c);
						boolean isNow = now.contains(c);
						if(!wasBefore && isNow) {
							db.addVisibility(txn, c, g);
							affected.add(c);
						} else if(wasBefore && !isNow) {
							db.removeVisibility(txn, c, g);
							affected.add(c);
						}
					}
					// Make the group invisible to future contacts
					db.setVisibleToAll(txn, g, false);
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

	public void setVisibleToAll(GroupId g, boolean all) throws DbException {
		Collection<ContactId> affected = new ArrayList<ContactId>();
		contactLock.readLock().lock();
		try {
			subscriptionLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsGroup(txn, g))
						throw new NoSuchSubscriptionException();
					// Make the group visible or invisible to future contacts
					db.setVisibleToAll(txn, g, all);
					if(all) {
						// Make the group visible to all current contacts
						Collection<ContactId> before = db.getVisibility(txn, g);
						before = new HashSet<ContactId>(before);
						for(ContactId c : db.getContactIds(txn)) {
							if(!before.contains(c)) {
								db.addVisibility(txn, c, g);
								affected.add(c);
							}
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

	public void checkFreeSpaceAndClean() throws DbException {
		long freeSpace = db.getFreeSpace();
		if(LOG.isLoggable(INFO)) LOG.info(freeSpace + " bytes free space");
		while(freeSpace < MIN_FREE_SPACE) {
			boolean expired = expireMessages(BYTES_PER_SWEEP);
			if(freeSpace < CRITICAL_FREE_SPACE && !expired) {
				// FIXME: Work out what to do here
				throw new Error("Disk space is critically low");
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
						for(MessageId m : expired) db.removeMessage(txn, m);
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
		callListeners(new MessageExpiredEvent());
		return true;
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
