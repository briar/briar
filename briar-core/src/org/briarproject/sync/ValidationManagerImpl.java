package org.briarproject.sync;

import com.google.inject.Inject;

import org.briarproject.api.UniqueId;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.NoSuchMessageException;
import org.briarproject.api.db.NoSuchSubscriptionException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.MessageAddedEvent;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.MessageValidator;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.util.ByteUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;

class ValidationManagerImpl implements ValidationManager, EventListener {

	private static final Logger LOG =
			Logger.getLogger(ValidationManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final Executor cryptoExecutor;
	private final EventBus eventBus;
	private final Map<ClientId, MessageValidator> validators;

	@Inject
	ValidationManagerImpl(DatabaseComponent db,
			@DatabaseExecutor Executor dbExecutor,
			@CryptoExecutor Executor cryptoExecutor, EventBus eventBus) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.cryptoExecutor = cryptoExecutor;
		this.eventBus = eventBus;
		validators = new ConcurrentHashMap<ClientId, MessageValidator>();
	}

	@Override
	public boolean start() {
		eventBus.addListener(this);
		return true;
	}

	@Override
	public boolean stop() {
		eventBus.removeListener(this);
		return true;
	}

	@Override
	public void setMessageValidator(ClientId c, MessageValidator v) {
		validators.put(c, v);
		getMessagesToValidate(c);
	}

	private void getMessagesToValidate(final ClientId c) {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					// TODO: Don't do all of this in a single DB task
					for (MessageId id : db.getMessagesToValidate(c)) {
						try {
							Message m = parseMessage(id, db.getRawMessage(id));
							validateMessage(m, c);
						} catch (NoSuchMessageException e) {
							LOG.info("Message removed before validation");
						}
					}
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

	private void validateMessage(final Message m, final ClientId c) {
		cryptoExecutor.execute(new Runnable() {
			public void run() {
				MessageValidator v = validators.get(c);
				if (v == null) {
					LOG.warning("No validator");
				} else {
					Metadata meta = v.validateMessage(m);
					storeValidationResult(m, c, meta);
				}
			}
		});
	}

	private void storeValidationResult(final Message m, final ClientId c,
			final Metadata meta) {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					if (meta == null) {
						db.setMessageValidity(m, c, false);
					} else {
						db.mergeMessageMetadata(m.getId(), meta);
						db.setMessageValidity(m, c, true);
					}
				} catch (NoSuchMessageException e) {
					LOG.info("Message removed during validation");
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
			MessageAddedEvent m = (MessageAddedEvent) e;
			// Validate the message if it wasn't created locally
			if (m.getContactId() != null) loadClientId(m.getMessage());
		}
	}

	private void loadClientId(final Message m) {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					ClientId c = db.getGroup(m.getGroupId()).getClientId();
					validateMessage(m, c);
				} catch (NoSuchSubscriptionException e) {
					LOG.info("Group removed before validation");
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}
}
