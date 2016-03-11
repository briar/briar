package org.briarproject.clients;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageQueueManager;
import org.briarproject.api.clients.QueueMessage;
import org.briarproject.api.clients.QueueMessageFactory;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.api.sync.ValidationManager.IncomingMessageHook;
import org.briarproject.util.ByteUtils;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static org.briarproject.api.clients.QueueMessage.QUEUE_MESSAGE_HEADER_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;

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
		saveQueueState(txn, queue.getId(), queueState);
		QueueMessage q = queueMessageFactory.createMessage(queue.getId(),
				timestamp, queuePosition, body);
		db.addLocalMessage(txn, q, queue.getClientId(), meta, true);
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

		QueueState(long outgoingPosition, long incomingPosition,
				TreeMap<Long, MessageId> pending) {
			this.outgoingPosition = outgoingPosition;
			this.incomingPosition = incomingPosition;
			this.pending = pending;
		}

		MessageId popIncomingMessageId() {
			Iterator<Entry<Long, MessageId>> it = pending.entrySet().iterator();
			if (!it.hasNext()) return null;
			Entry<Long, MessageId> e = it.next();
			if (!e.getKey().equals(incomingPosition)) return null;
			it.remove();
			incomingPosition++;
			return e.getValue();
		}
	}

	private static class DelegatingMessageValidator
			implements ValidationManager.MessageValidator {

		private final QueueMessageValidator delegate;

		DelegatingMessageValidator(QueueMessageValidator delegate) {
			this.delegate = delegate;
		}

		@Override
		public Metadata validateMessage(Message m, Group g) {
			byte[] raw = m.getRaw();
			if (raw.length < QUEUE_MESSAGE_HEADER_LENGTH) return null;
			long queuePosition = ByteUtils.readUint64(raw,
					MESSAGE_HEADER_LENGTH);
			if (queuePosition < 0) return null;
			QueueMessage q = new QueueMessage(m.getId(), m.getGroupId(),
					m.getTimestamp(), queuePosition, raw);
			return delegate.validateMessage(q, g);
		}
	}

	private class DelegatingIncomingMessageHook implements IncomingMessageHook {

		private final IncomingQueueMessageHook delegate;

		DelegatingIncomingMessageHook(IncomingQueueMessageHook delegate) {
			this.delegate = delegate;
		}

		@Override
		public void incomingMessage(Transaction txn, Message m, Metadata meta)
				throws DbException {
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
				// The message is in order, pass it to the delegate
				LOG.info("Message is in order, delivering");
				QueueMessage q = new QueueMessage(m.getId(), m.getGroupId(),
						m.getTimestamp(), queuePosition, m.getRaw());
				delegate.incomingMessage(txn, q, meta);
				queueState.incomingPosition++;
				// Pass any consecutive messages to the delegate
				MessageId id;
				while ((id = queueState.popIncomingMessageId()) != null) {
					byte[] raw = db.getRawMessage(txn, id);
					meta = db.getMessageMetadata(txn, id);
					q = queueMessageFactory.createMessage(id, raw);
					if (LOG.isLoggable(INFO)) {
						LOG.info("Delivering pending message with position "
								+ q.getQueuePosition());
					}
					delegate.incomingMessage(txn, q, meta);
				}
				saveQueueState(txn, m.getGroupId(), queueState);
			}
		}
	}
}
