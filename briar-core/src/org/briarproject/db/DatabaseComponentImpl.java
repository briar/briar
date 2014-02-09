package org.briarproject.db;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.db.DatabaseConstants.BYTES_PER_SWEEP;
import static org.briarproject.db.DatabaseConstants.CRITICAL_FREE_SPACE;
import static org.briarproject.db.DatabaseConstants.MAX_OFFERED_MESSAGES;
import static org.briarproject.db.DatabaseConstants.MAX_TRANSACTIONS_BETWEEN_SPACE_CHECKS;
import static org.briarproject.db.DatabaseConstants.MIN_FREE_SPACE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.api.Author;
import org.briarproject.api.AuthorId;
import org.briarproject.api.Contact;
import org.briarproject.api.ContactId;
import org.briarproject.api.LocalAuthor;
import org.briarproject.api.TransportConfig;
import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.db.ContactExistsException;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.LocalAuthorExistsException;
import org.briarproject.api.db.MessageHeader;
import org.briarproject.api.db.NoSuchContactException;
import org.briarproject.api.db.NoSuchLocalAuthorException;
import org.briarproject.api.db.NoSuchMessageException;
import org.briarproject.api.db.NoSuchSubscriptionException;
import org.briarproject.api.db.NoSuchTransportException;
import org.briarproject.api.event.ContactAddedEvent;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.LocalAuthorAddedEvent;
import org.briarproject.api.event.LocalAuthorRemovedEvent;
import org.briarproject.api.event.LocalSubscriptionsUpdatedEvent;
import org.briarproject.api.event.LocalTransportsUpdatedEvent;
import org.briarproject.api.event.MessageAddedEvent;
import org.briarproject.api.event.MessageExpiredEvent;
import org.briarproject.api.event.MessageRequestedEvent;
import org.briarproject.api.event.MessageToAckEvent;
import org.briarproject.api.event.MessageToRequestEvent;
import org.briarproject.api.event.RemoteRetentionTimeUpdatedEvent;
import org.briarproject.api.event.RemoteSubscriptionsUpdatedEvent;
import org.briarproject.api.event.RemoteTransportsUpdatedEvent;
import org.briarproject.api.event.SubscriptionAddedEvent;
import org.briarproject.api.event.SubscriptionRemovedEvent;
import org.briarproject.api.event.TransportAddedEvent;
import org.briarproject.api.event.TransportRemovedEvent;
import org.briarproject.api.lifecycle.ShutdownManager;
import org.briarproject.api.messaging.Ack;
import org.briarproject.api.messaging.Group;
import org.briarproject.api.messaging.GroupId;
import org.briarproject.api.messaging.GroupStatus;
import org.briarproject.api.messaging.Message;
import org.briarproject.api.messaging.MessageId;
import org.briarproject.api.messaging.Offer;
import org.briarproject.api.messaging.Request;
import org.briarproject.api.messaging.RetentionAck;
import org.briarproject.api.messaging.RetentionUpdate;
import org.briarproject.api.messaging.SubscriptionAck;
import org.briarproject.api.messaging.SubscriptionUpdate;
import org.briarproject.api.messaging.TransportAck;
import org.briarproject.api.messaging.TransportUpdate;
import org.briarproject.api.system.Clock;
import org.briarproject.api.transport.Endpoint;
import org.briarproject.api.transport.TemporarySecret;

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

	private final Collection<EventListener> listeners =
			new CopyOnWriteArrayList<EventListener>();

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

	public void addListener(EventListener l) {
		listeners.add(l);
	}

	public void removeListener(EventListener l) {
		listeners.remove(l);
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
	private void callListeners(Event e) {
		for(EventListener l : listeners) l.eventOccurred(e);
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
	 * Stores a message, initialises its status with respect to each contact,
	 * and marks it as read if it was locally generated.
	 * <p>
	 * Locking: contact read, message write, subscription read.
	 * @param sender null for a locally generated message.
	 */
	private void addMessage(T txn, Message m, ContactId sender)
			throws DbException {
		if(sender == null) {
			db.addMessage(txn, m, true);
			db.setReadFlag(txn, m.getId(), true);
		} else {
			db.addMessage(txn, m, false);
		}
		Group g = m.getGroup();
		Collection<ContactId> visibility = db.getVisibility(txn, g.getId());
		visibility = new HashSet<ContactId>(visibility);
		for(ContactId c : db.getContactIds(txn)) {
			if(visibility.contains(c)) {
				boolean offered = db.removeOfferedMessage(txn, c, m.getId());
				boolean seen = offered || c.equals(sender);
				db.addStatus(txn, c, m.getId(), offered, seen);
			} else {
				if(c.equals(sender)) throw new IllegalStateException();
				db.addStatus(txn, c, m.getId(), false, false);
			}
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

	public Ack generateAck(ContactId c, int maxMessages) throws DbException {
		Collection<MessageId> ids;
		contactLock.readLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					ids = db.getMessagesToAck(txn, c, maxMessages);
					if(!ids.isEmpty()) db.lowerAckFlag(txn, c, ids);
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
		if(ids.isEmpty()) return null;
		return new Ack(ids);
	}

	public Collection<byte[]> generateBatch(ContactId c, int maxLength,
			long maxLatency) throws DbException {
		Collection<MessageId> ids;
		List<byte[]> messages = new ArrayList<byte[]>();
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
						ids = db.getMessagesToSend(txn, c, maxLength);
						for(MessageId m : ids) {
							messages.add(db.getRawMessage(txn, m));
							db.updateExpiryTime(txn, c, m, maxLatency);
						}
						if(!ids.isEmpty()) db.lowerRequestedFlag(txn, c, ids);
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
		if(messages.isEmpty()) return null;
		return Collections.unmodifiableList(messages);
	}

	public Offer generateOffer(ContactId c, int maxMessages, long maxLatency)
			throws DbException {
		Collection<MessageId> ids;
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
						ids = db.getMessagesToOffer(txn, c, maxMessages);
						for(MessageId m : ids)
							db.updateExpiryTime(txn, c, m, maxLatency);
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
		if(ids.isEmpty()) return null;
		return new Offer(ids);
	}

	public Request generateRequest(ContactId c, int maxMessages)
			throws DbException {
		Collection<MessageId> ids;
		contactLock.readLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					ids = db.getMessagesToRequest(txn, c, maxMessages);
					if(!ids.isEmpty()) db.removeOfferedMessages(txn, c, ids);
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
		if(ids.isEmpty()) return null;
		return new Request(ids);
	}

	public Collection<byte[]> generateRequestedBatch(ContactId c, int maxLength,
			long maxLatency) throws DbException {
		Collection<MessageId> ids;
		List<byte[]> messages = new ArrayList<byte[]>();
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
						ids = db.getRequestedMessagesToSend(txn, c, maxLength);
						for(MessageId m : ids) {
							messages.add(db.getRawMessage(txn, m));
							db.updateExpiryTime(txn, c, m, maxLatency);
						}
						if(!ids.isEmpty()) db.lowerRequestedFlag(txn, c, ids);
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
		if(messages.isEmpty()) return null;
		return Collections.unmodifiableList(messages);
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

	public GroupId getInboxGroupId(ContactId c) throws DbException {
		contactLock.readLock().lock();
		try {
			subscriptionLock.readLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					GroupId inbox = db.getInboxGroupId(txn, c);
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
					for(MessageId m : a.getMessageIds()) {
						if(db.containsVisibleMessage(txn, c, m))
							db.raiseSeenFlag(txn, c, m);
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
						if(visible) db.raiseAckFlag(txn, c, m.getId());
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
		if(visible) callListeners(new MessageToAckEvent(c));
		if(!duplicate) callListeners(new MessageAddedEvent(m.getGroup(), c));
	}

	public void receiveOffer(ContactId c, Offer o) throws DbException {
		boolean ack = false, request = false;
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
						int count = db.countOfferedMessages(txn, c);
						for(MessageId m : o.getMessageIds()) {
							if(db.containsVisibleMessage(txn, c, m)) {
								db.raiseSeenFlag(txn, c, m);
								db.raiseAckFlag(txn, c, m);
								ack = true;
							} else if(count < MAX_OFFERED_MESSAGES) {
								db.addOfferedMessage(txn, c, m);
								request = true;
								count++;
							}
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
		if(ack) callListeners(new MessageToAckEvent(c));
		if(request) callListeners(new MessageToRequestEvent(c));
	}

	public void receiveRequest(ContactId c, Request r) throws DbException {
		boolean requested = false;
		contactLock.readLock().lock();
		try {
			messageLock.writeLock().lock();
			try {
				T txn = db.startTransaction();
				try {
					if(!db.containsContact(txn, c))
						throw new NoSuchContactException();
					for(MessageId m : r.getMessageIds()) {
						if(db.containsVisibleMessage(txn, c, m)) {
							db.raiseRequestedFlag(txn, c, m);
							db.resetExpiryTime(txn, c, m);
							requested = true;
						}
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
			contactLock.readLock().unlock();
		}
		if(requested) callListeners(new MessageRequestedEvent(c));
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
									GroupId g = db.getInboxGroupId(txn, c);
									if(g != null) db.removeGroup(txn, g);
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
										for(ContactId c : affected) {
											GroupId g = db.getInboxGroupId(txn, c);
											if(g != null) db.removeGroup(txn, g);
										}
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

	public void setReadFlag(MessageId m, boolean read) throws DbException {
		messageLock.writeLock().lock();
		try {
			T txn = db.startTransaction();
			try {
				if(!db.containsMessage(txn, m))
					throw new NoSuchMessageException();
				db.setReadFlag(txn, m, read);
				db.commitTransaction(txn);
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
		if(db.getTransactionCount() > MAX_TRANSACTIONS_BETWEEN_SPACE_CHECKS) {
			db.resetTransactionCount();
			return true;
		}
		return false;
	}
}
