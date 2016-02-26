package org.briarproject.sync;

import com.google.inject.Inject;

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
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.MessageValidator;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.util.ByteUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;

class ValidationManagerImpl implements ValidationManager, Service,
		EventListener {

	private static final Logger LOG =
			Logger.getLogger(ValidationManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final Executor cryptoExecutor;
	private final Map<ClientId, MessageValidator> validators;
	private final List<ValidationHook> hooks;

	@Inject
	ValidationManagerImpl(DatabaseComponent db,
			@DatabaseExecutor Executor dbExecutor,
			@CryptoExecutor Executor cryptoExecutor) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.cryptoExecutor = cryptoExecutor;
		validators = new ConcurrentHashMap<ClientId, MessageValidator>();
		hooks = new CopyOnWriteArrayList<ValidationHook>();
	}

	@Override
	public boolean start() {
		for (ClientId c : validators.keySet()) getMessagesToValidate(c);
		return true;
	}

	@Override
	public boolean stop() {
		return true;
	}

	@Override
	public void registerMessageValidator(ClientId c, MessageValidator v) {
		validators.put(c, v);
	}

	@Override
	public void registerValidationHook(ValidationHook hook) {
		hooks.add(hook);
	}

	private void getMessagesToValidate(final ClientId c) {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					Queue<MessageId> unvalidated = new LinkedList<MessageId>();
					Transaction txn = db.startTransaction();
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
			public void run() {
				try {
					Message m = null;
					Group g = null;
					Transaction txn = db.startTransaction();
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
			public void run() {
				MessageValidator v = validators.get(g.getClientId());
				if (v == null) {
					LOG.warning("No validator");
				} else {
					Metadata meta = v.validateMessage(m, g);
					storeValidationResult(m, g.getClientId(), meta);
				}
			}
		});
	}

	private void storeValidationResult(final Message m, final ClientId c,
			final Metadata meta) {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					Transaction txn = db.startTransaction();
					try {
						if (meta == null) {
							db.setMessageValid(txn, m, c, false);
						} else {
							db.mergeMessageMetadata(txn, m.getId(), meta);
							db.setMessageValid(txn, m, c, true);
							db.setMessageShared(txn, m, true);
							for (ValidationHook hook : hooks)
								hook.validatingMessage(txn, m, c, meta);
						}
						txn.setComplete();
					} finally {
						db.endTransaction(txn);
					}
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
			public void run() {
				try {
					Group g;
					Transaction txn = db.startTransaction();
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
