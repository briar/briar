package org.briarproject.sync;

import org.briarproject.api.UniqueId;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.NoSuchGroupException;
import org.briarproject.api.db.NoSuchMessageException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.MessageAddedEvent;
import org.briarproject.api.lifecycle.Service;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.InvalidMessageException;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.api.sync.MessageContext;
import org.briarproject.util.ByteUtils;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.api.sync.ValidationManager.State.DELIVERED;
import static org.briarproject.api.sync.ValidationManager.State.INVALID;
import static org.briarproject.api.sync.ValidationManager.State.PENDING;
import static org.briarproject.api.sync.ValidationManager.State.VALID;

class ValidationManagerImpl implements ValidationManager, Service,
		EventListener {

	private static final Logger LOG =
			Logger.getLogger(ValidationManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final Executor cryptoExecutor;
	private final Map<ClientId, MessageValidator> validators;
	private final Map<ClientId, IncomingMessageHook> hooks;
	private final AtomicBoolean used = new AtomicBoolean(false);

	@Inject
	ValidationManagerImpl(DatabaseComponent db,
			@DatabaseExecutor Executor dbExecutor,
			@CryptoExecutor Executor cryptoExecutor) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.cryptoExecutor = cryptoExecutor;
		validators = new ConcurrentHashMap<ClientId, MessageValidator>();
		hooks = new ConcurrentHashMap<ClientId, IncomingMessageHook>();
	}

	@Override
	public void startService() {
		if (used.getAndSet(true)) throw new IllegalStateException();
		for (ClientId c : validators.keySet()) {
			validateOutstandingMessages(c);
			deliverOutstandingMessages(c);
		}
	}

	@Override
	public void stopService() {
	}

	@Override
	public void registerMessageValidator(ClientId c, MessageValidator v) {
		validators.put(c, v);
	}

	@Override
	public void registerIncomingMessageHook(ClientId c,
			IncomingMessageHook hook) {
		hooks.put(c, hook);
	}

	private void validateOutstandingMessages(final ClientId c) {
		dbExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					Queue<MessageId> unvalidated = new LinkedList<MessageId>();
					Transaction txn = db.startTransaction(true);
					try {
						unvalidated.addAll(db.getMessagesToValidate(txn, c));
						txn.setComplete();
					} finally {
						db.endTransaction(txn);
					}
					validateNextMessage(unvalidated);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void validateNextMessage(final Queue<MessageId> unvalidated) {
		if (unvalidated.isEmpty()) return;
		dbExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					Message m = null;
					Group g = null;
					Transaction txn = db.startTransaction(true);
					try {
						MessageId id = unvalidated.poll();
						byte[] raw = db.getRawMessage(txn, id);
						m = parseMessage(id, raw);
						g = db.getGroup(txn, m.getGroupId());
						txn.setComplete();
					} catch (NoSuchMessageException e) {
						LOG.info("Message removed before validation");
						// Continue to next message
					} catch (NoSuchGroupException e) {
						LOG.info("Group removed before validation");
						// Continue to next message
					} finally {
						if (!txn.isComplete()) txn.setComplete();
						db.endTransaction(txn);
					}
					if (m != null && g != null) validateMessage(m, g);
					validateNextMessage(unvalidated);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void deliverOutstandingMessages(final ClientId c) {
		dbExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					Queue<MessageId> validated = new LinkedList<MessageId>();
					Queue<MessageId> pending = new LinkedList<MessageId>();
					Transaction txn = db.startTransaction(true);
					try {
						validated.addAll(db.getMessagesToDeliver(txn, c));
						pending.addAll(db.getPendingMessages(txn, c));
						txn.setComplete();
					} finally {
						db.endTransaction(txn);
					}
					deliverNextMessage(validated);
					deliverNextPendingMessage(pending);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void deliverNextMessage(final Queue<MessageId> validated) {
		if (validated.isEmpty()) return;
		dbExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					Message m = null;
					Group g = null;
					Metadata meta = null;
					Transaction txn = db.startTransaction(true);
					try {
						MessageId id = validated.poll();
						byte[] raw = db.getRawMessage(txn, id);
						m = parseMessage(id, raw);
						g = db.getGroup(txn, m.getGroupId());
						meta = db.getMessageMetadata(txn, id);
						txn.setComplete();
					} finally {
						db.endTransaction(txn);
					}
					if (g != null) deliverMessage(m, g.getClientId(), meta);
					deliverNextMessage(validated);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void deliverNextPendingMessage(final Queue<MessageId> pending) {
		if (pending.isEmpty()) return;
		dbExecutor.execute(new Runnable() {
			@Override
			public void run() {
				Message m = null;
				ClientId c = null;
				try {
					boolean allDelivered = true;
					Metadata meta = null;
					Transaction txn = db.startTransaction(true);
					try {
						MessageId id = pending.poll();
						byte[] raw = db.getRawMessage(txn, id);
						m = parseMessage(id, raw);
						Group g = db.getGroup(txn, m.getGroupId());
						c = g.getClientId();

						// check if a dependency is invalid
						Map<MessageId, State> states =
								db.getMessageDependencies(txn, id);
						for (Entry<MessageId, State> d : states.entrySet()) {
							if (d.getValue() == INVALID) {
								throw new InvalidMessageException(
										"Invalid Dependency");
							}
							if (d.getValue() != DELIVERED) allDelivered = false;
						}
						if(allDelivered) {
							meta = db.getMessageMetadata(txn, id);
						}
						txn.setComplete();
					} finally {
						if (!txn.isComplete()) txn.setComplete();
						db.endTransaction(txn);
					}
					if (c != null && allDelivered) deliverMessage(m, c, meta);
					deliverNextPendingMessage(pending);
				} catch(InvalidMessageException e) {
					if (LOG.isLoggable(INFO))
						LOG.log(INFO, e.toString(), e);
					markMessageInvalid(m, c);
					deliverNextPendingMessage(pending);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private Message parseMessage(MessageId id, byte[] raw) {
		if (raw.length <= MESSAGE_HEADER_LENGTH)
			throw new IllegalArgumentException();
		byte[] groupId = new byte[UniqueId.LENGTH];
		System.arraycopy(raw, 0, groupId, 0, UniqueId.LENGTH);
		long timestamp = ByteUtils.readUint64(raw, UniqueId.LENGTH);
		return new Message(id, new GroupId(groupId), timestamp, raw);
	}

	private void validateMessage(final Message m, final Group g) {
		cryptoExecutor.execute(new Runnable() {
			@Override
			public void run() {
				MessageValidator v = validators.get(g.getClientId());
				if (v == null) {
					LOG.warning("No validator");
				} else {
					try {
						MessageContext context = v.validateMessage(m, g);
						storeMessageContext(m, g.getClientId(), context);
					} catch (InvalidMessageException e) {
						if (LOG.isLoggable(INFO))
							LOG.log(INFO, e.toString(), e);
						markMessageInvalid(m, g.getClientId());
					}
				}
			}
		});
	}

	private void storeMessageContext(final Message m, final ClientId c,
			final MessageContext result) {
		dbExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					State newState = null;
					Metadata meta = null;
					Transaction txn = db.startTransaction(false);
					try {
						// store dependencies
						Collection<MessageId> dependencies =
								result.getDependencies();
						if (dependencies != null && dependencies.size() > 0) {
							db.addMessageDependencies(txn, m, dependencies);
						}
						// check if a dependency is invalid
						// and if all dependencies have been delivered
						Map<MessageId, State> states =
								db.getMessageDependencies(txn, m.getId());
						newState = VALID;
						for (Entry<MessageId, State> d : states.entrySet()) {
							if (d.getValue() == INVALID) {
								throw new InvalidMessageException(
										"Dependency Invalid");
							}
							if (d.getValue() != DELIVERED) {
								newState = PENDING;
								LOG.info("depend. undelivered, set to PENDING");
								break;
							}
						}
						// save metadata and new message state
						meta = result.getMetadata();
						db.mergeMessageMetadata(txn, m.getId(), meta);
						db.setMessageState(txn, m, c, newState);
						txn.setComplete();
					} finally {
						if (!txn.isComplete()) txn.setComplete();
						db.endTransaction(txn);
					}
					// deliver message if valid
					if (newState == VALID) {
						deliverMessage(m, c, meta);
					}
				} catch (InvalidMessageException e) {
					if (LOG.isLoggable(INFO))
						LOG.log(INFO, e.toString(), e);
					markMessageInvalid(m, c);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void deliverMessage(final Message m, final ClientId c,
			final Metadata meta) {
		dbExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					Queue<MessageId> pending = new LinkedList<MessageId>();
					Transaction txn = db.startTransaction(false);
					try {
						IncomingMessageHook hook = hooks.get(c);
						if (hook != null)
							hook.incomingMessage(txn, m, meta);

						// check if message was deleted by client
						if (db.getRawMessage(txn, m.getId()) == null) {
							throw new InvalidMessageException(
									"Deleted by Client");
						}

						db.setMessageShared(txn, m, true);
						db.setMessageState(txn, m, c, DELIVERED);

						// deliver pending dependents
						Map<MessageId, State> dependents =
								db.getMessageDependents(txn, m.getId());
						for (Entry<MessageId, State> i : dependents
								.entrySet()) {
							if (i.getValue() != PENDING) continue;

							// check that all dependencies are delivered
							Map<MessageId, State> dependencies =
									db.getMessageDependencies(txn, i.getKey());
							for (Entry<MessageId, State> j : dependencies
									.entrySet()) {
								if (j.getValue() != DELIVERED) return;
							}
							pending.add(i.getKey());
						}
						txn.setComplete();
					} finally {
						if (!txn.isComplete()) txn.setComplete();
						db.endTransaction(txn);
					}
					deliverNextMessage(pending);
				} catch (InvalidMessageException e) {
					if (LOG.isLoggable(INFO))
						LOG.log(INFO, e.toString(), e);
					markMessageInvalid(m, c);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void markMessageInvalid(final Message m, final ClientId c) {
		dbExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					Queue<MessageId> invalid = new LinkedList<MessageId>();
					Transaction txn = db.startTransaction(false);
					try {
						Map<MessageId, State> dependents =
								db.getMessageDependents(txn, m.getId());
						db.setMessageState(txn, m, c, INVALID);
						db.deleteMessage(txn, m.getId());
						db.deleteMessageMetadata(txn, m.getId());

						// recursively invalidate all messages that depend on m
						// TODO check that cycles are properly taken care of
						for (Entry<MessageId, State> i : dependents
								.entrySet()) {
							if (i.getValue() != INVALID) {
								invalid.add(i.getKey());
							}
						}
						txn.setComplete();
					} finally {
						db.endTransaction(txn);
					}
					markNextMessageInvalid(invalid);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void markNextMessageInvalid(final Queue<MessageId> invalid) {
		if (invalid.isEmpty()) return;
		dbExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					Message m = null;
					Group g = null;
					Transaction txn = db.startTransaction(true);
					try {
						MessageId id = invalid.poll();
						byte[] raw = db.getRawMessage(txn, id);
						m = parseMessage(id, raw);
						g = db.getGroup(txn, m.getGroupId());
						txn.setComplete();
					} finally {
						db.endTransaction(txn);
					}
					if (g != null) markMessageInvalid(m, g.getClientId());
					markNextMessageInvalid(invalid);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof MessageAddedEvent) {
			// Validate the message if it wasn't created locally
			MessageAddedEvent m = (MessageAddedEvent) e;
			if (m.getContactId() != null) loadGroupAndValidate(m.getMessage());
		}
	}

	private void loadGroupAndValidate(final Message m) {
		dbExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					Group g;
					Transaction txn = db.startTransaction(true);
					try {
						g = db.getGroup(txn, m.getGroupId());
						txn.setComplete();
					} finally {
						db.endTransaction(txn);
					}
					validateMessage(m, g);
				} catch (NoSuchGroupException e) {
					LOG.info("Group removed before validation");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}
}
