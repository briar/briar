package org.briarproject.briar.client;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.InvalidMessageException;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageContext;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.ValidationManager;
import org.briarproject.bramble.api.sync.ValidationManager.IncomingMessageHook;
import org.briarproject.bramble.api.sync.ValidationManager.MessageValidator;
import org.briarproject.bramble.util.ByteUtils;
import org.briarproject.briar.api.client.MessageQueueManager;
import org.briarproject.briar.api.client.QueueMessage;
import org.briarproject.briar.api.client.QueueMessageFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static org.briarproject.bramble.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.briar.api.client.QueueMessage.QUEUE_MESSAGE_HEADER_LENGTH;

@Immutable
@NotNullByDefault
class MessageQueueManagerImpl implements MessageQueueManager {

	private static final String OUTGOING_POSITION_KEY = "nextOut";
	private static final String INCOMING_POSITION_KEY = "nextIn";
	private static final String PENDING_MESSAGES_KEY = "pending";

	private static final Logger LOG =
			Logger.getLogger(MessageQueueManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final ClientHelper clientHelper;
	private final QueueMessageFactory queueMessageFactory;
	private final ValidationManager validationManager;

	@Inject
	MessageQueueManagerImpl(DatabaseComponent db, ClientHelper clientHelper,
			QueueMessageFactory queueMessageFactory,
			ValidationManager validationManager) {
		this.db = db;
		this.clientHelper = clientHelper;
		this.queueMessageFactory = queueMessageFactory;
		this.validationManager = validationManager;
	}

	@Override
	public QueueMessage sendMessage(Transaction txn, Group queue,
			long timestamp, byte[] body, Metadata meta) throws DbException {
		QueueState queueState = loadQueueState(txn, queue.getId());
		long queuePosition = queueState.outgoingPosition;
		queueState.outgoingPosition++;
		if (LOG.isLoggable(INFO))
			LOG.info("Sending message with position " + queuePosition);
		saveQueueState(txn, queue.getId(), queueState);
		QueueMessage q = queueMessageFactory.createMessage(queue.getId(),
				timestamp, queuePosition, body);
		db.addLocalMessage(txn, q, meta, true);
		return q;
	}

	@Override
	public void registerMessageValidator(ClientId c, QueueMessageValidator v) {
		validationManager.registerMessageValidator(c,
				new DelegatingMessageValidator(v));
	}

	@Override
	public void registerIncomingMessageHook(ClientId c,
			IncomingQueueMessageHook hook) {
		validationManager.registerIncomingMessageHook(c,
				new DelegatingIncomingMessageHook(hook));
	}

	private QueueState loadQueueState(Transaction txn, GroupId g)
			throws DbException {
		try {
			TreeMap<Long, MessageId> pending = new TreeMap<Long, MessageId>();
			Metadata groupMeta = db.getGroupMetadata(txn, g);
			byte[] raw = groupMeta.get(QUEUE_STATE_KEY);
			if (raw == null) return new QueueState(0, 0, pending);
			BdfDictionary d = clientHelper.toDictionary(raw, 0, raw.length);
			long outgoingPosition = d.getLong(OUTGOING_POSITION_KEY);
			long incomingPosition = d.getLong(INCOMING_POSITION_KEY);
			BdfList pendingList = d.getList(PENDING_MESSAGES_KEY);
			for (int i = 0; i < pendingList.size(); i++) {
				BdfList item = pendingList.getList(i);
				if (item.size() != 2) throw new FormatException();
				pending.put(item.getLong(0), new MessageId(item.getRaw(1)));
			}
			return new QueueState(outgoingPosition, incomingPosition, pending);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private void saveQueueState(Transaction txn, GroupId g,
			QueueState queueState) throws DbException {
		try {
			BdfDictionary d = new BdfDictionary();
			d.put(OUTGOING_POSITION_KEY, queueState.outgoingPosition);
			d.put(INCOMING_POSITION_KEY, queueState.incomingPosition);
			BdfList pendingList = new BdfList();
			for (Entry<Long, MessageId> e : queueState.pending.entrySet())
				pendingList.add(BdfList.of(e.getKey(), e.getValue()));
			d.put(PENDING_MESSAGES_KEY, pendingList);
			Metadata groupMeta = new Metadata();
			groupMeta.put(QUEUE_STATE_KEY, clientHelper.toByteArray(d));
			db.mergeGroupMetadata(txn, g, groupMeta);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	private static class QueueState {

		private long outgoingPosition, incomingPosition;
		private final TreeMap<Long, MessageId> pending;

		private QueueState(long outgoingPosition, long incomingPosition,
				TreeMap<Long, MessageId> pending) {
			this.outgoingPosition = outgoingPosition;
			this.incomingPosition = incomingPosition;
			this.pending = pending;
		}

		@Nullable
		MessageId popIncomingMessageId() {
			Iterator<Entry<Long, MessageId>> it = pending.entrySet().iterator();
			if (!it.hasNext()) {
				LOG.info("No pending messages");
				return null;
			}
			Entry<Long, MessageId> e = it.next();
			if (!e.getKey().equals(incomingPosition)) {
				if (LOG.isLoggable(INFO)) {
					LOG.info("First pending message is " + e.getKey() + ", "
							+ " expecting " + incomingPosition);
				}
				return null;
			}
			if (LOG.isLoggable(INFO))
				LOG.info("Removing pending message " + e.getKey());
			it.remove();
			incomingPosition++;
			return e.getValue();
		}
	}

	@NotNullByDefault
	private static class DelegatingMessageValidator
			implements MessageValidator {

		private final QueueMessageValidator delegate;

		private DelegatingMessageValidator(QueueMessageValidator delegate) {
			this.delegate = delegate;
		}

		@Override
		public MessageContext validateMessage(Message m, Group g)
				throws InvalidMessageException {
			byte[] raw = m.getRaw();
			if (raw.length < QUEUE_MESSAGE_HEADER_LENGTH)
				throw new InvalidMessageException();
			long queuePosition = ByteUtils.readUint64(raw,
					MESSAGE_HEADER_LENGTH);
			if (queuePosition < 0) throw new InvalidMessageException();
			QueueMessage q = new QueueMessage(m.getId(), m.getGroupId(),
					m.getTimestamp(), queuePosition, raw);
			return delegate.validateMessage(q, g);
		}
	}

	@NotNullByDefault
	private class DelegatingIncomingMessageHook implements IncomingMessageHook {

		private final IncomingQueueMessageHook delegate;

		private DelegatingIncomingMessageHook(
				IncomingQueueMessageHook delegate) {
			this.delegate = delegate;
		}

		@Override
		public boolean incomingMessage(Transaction txn, Message m,
				Metadata meta) throws DbException, InvalidMessageException {
			long queuePosition = ByteUtils.readUint64(m.getRaw(),
					MESSAGE_HEADER_LENGTH);
			QueueState queueState = loadQueueState(txn, m.getGroupId());
			if (LOG.isLoggable(INFO)) {
				LOG.info("Received message with position  "
						+ queuePosition + ", expecting "
						+ queueState.incomingPosition);
			}
			if (queuePosition < queueState.incomingPosition) {
				// A message with this queue position has already been seen
				LOG.warning("Deleting message with duplicate position");
				db.deleteMessage(txn, m.getId());
				db.deleteMessageMetadata(txn, m.getId());
			} else if (queuePosition > queueState.incomingPosition) {
				// The message is out of order, add it to the pending list
				LOG.info("Message is out of order, adding to pending list");
				queueState.pending.put(queuePosition, m.getId());
				saveQueueState(txn, m.getGroupId(), queueState);
			} else {
				// The message is in order
				LOG.info("Message is in order, delivering");
				QueueMessage q = new QueueMessage(m.getId(), m.getGroupId(),
						m.getTimestamp(), queuePosition, m.getRaw());
				queueState.incomingPosition++;
				// Collect any consecutive messages
				List<MessageId> consecutive = new ArrayList<MessageId>();
				MessageId next;
				while ((next = queueState.popIncomingMessageId()) != null)
					consecutive.add(next);
				// Save the queue state before passing control to the delegate
				saveQueueState(txn, m.getGroupId(), queueState);
				// Deliver the messages to the delegate
				delegate.incomingMessage(txn, q, meta);
				for (MessageId id : consecutive) {
					byte[] raw = db.getRawMessage(txn, id);
					if (raw == null) throw new DbException();
					meta = db.getMessageMetadata(txn, id);
					q = queueMessageFactory.createMessage(id, raw);
					if (LOG.isLoggable(INFO)) {
						LOG.info("Delivering pending message with position "
								+ q.getQueuePosition());
					}
					delegate.incomingMessage(txn, q, meta);
				}
			}
			// message queues are only useful for groups with two members
			// so messages don't need to be shared
			return false;
		}
	}
}
