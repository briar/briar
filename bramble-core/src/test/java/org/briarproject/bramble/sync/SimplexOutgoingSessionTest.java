package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.sync.Ack;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.SyncRecordWriter;
import org.briarproject.bramble.api.sync.SyncSessionId;
import org.briarproject.bramble.api.sync.Versions;
import org.briarproject.bramble.api.transport.StreamWriter;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.DbExpectations;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.briarproject.bramble.api.record.Record.MAX_RECORD_PAYLOAD_BYTES;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_IDS;
import static org.briarproject.bramble.test.TestUtils.getContactId;
import static org.briarproject.bramble.test.TestUtils.getMessage;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.getTransportId;

public class SimplexOutgoingSessionTest extends BrambleMockTestCase {

	private static final int MAX_LATENCY = Integer.MAX_VALUE;

	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final EventBus eventBus = context.mock(EventBus.class);
	private final StreamWriter streamWriter = context.mock(StreamWriter.class);
	private final SyncRecordWriter recordWriter =
			context.mock(SyncRecordWriter.class);

	private final ContactId contactId = getContactId();
	private final TransportId transportId = getTransportId();
	private final Ack ack =
			new Ack(singletonList(new MessageId(getRandomId())));
	private final Message message = getMessage(new GroupId(getRandomId()),
			MAX_MESSAGE_BODY_LENGTH);
	private final Message message1 = getMessage(new GroupId(getRandomId()),
			MAX_MESSAGE_BODY_LENGTH);

	@Test
	public void testNothingToSend() throws Exception {
		SimplexOutgoingSession session = createSession(false, null);

		Transaction noAckTxn = new Transaction(null, false);
		Transaction noMsgTxn = new Transaction(null, false);

		context.checking(new DbExpectations() {{
			// Add listener
			oneOf(eventBus).addListener(session);
			// Send the protocol versions
			oneOf(recordWriter).writeVersions(with(any(Versions.class)));
			// No acks to send
			oneOf(db).transactionWithNullableResult(with(false),
					withNullableDbCallable(noAckTxn));
			oneOf(db).generateAck(noAckTxn, contactId, MAX_MESSAGE_IDS);
			will(returnValue(null));
			// No messages to send
			oneOf(db).transactionWithNullableResult(with(false),
					withNullableDbCallable(noMsgTxn));
			oneOf(db).generateBatch(noMsgTxn, contactId,
					MAX_RECORD_PAYLOAD_BYTES, MAX_LATENCY);
			will(returnValue(null));
			// Send the end of stream marker
			oneOf(streamWriter).sendEndOfStream();
			// Remove listener
			oneOf(eventBus).removeListener(session);
		}});

		session.run();
	}

	@Test
	public void testNothingToSendEagerly() throws Exception {
		SimplexOutgoingSession session = createSession(true, null);

		Transaction noAckTxn = new Transaction(null, false);
		Transaction noIdsTxn = new Transaction(null, true);

		context.checking(new DbExpectations() {{
			// Add listener
			oneOf(eventBus).addListener(session);
			// Send the protocol versions
			oneOf(recordWriter).writeVersions(with(any(Versions.class)));
			// No acks to send
			oneOf(db).transactionWithNullableResult(with(false),
					withNullableDbCallable(noAckTxn));
			oneOf(db).generateAck(noAckTxn, contactId, MAX_MESSAGE_IDS);
			will(returnValue(null));
			// No messages to send
			oneOf(db).transactionWithResult(with(true),
					withDbCallable(noIdsTxn));
			oneOf(db).getUnackedMessagesToSend(noIdsTxn, contactId);
			will(returnValue(emptyMap()));
			// Send the end of stream marker
			oneOf(streamWriter).sendEndOfStream();
			// Remove listener
			oneOf(eventBus).removeListener(session);
		}});

		session.run();
	}

	@Test
	public void testNothingToSendWithSyncSessionId() throws Exception {
		SyncSessionId syncSessionId = new SyncSessionId(getRandomId());
		SimplexOutgoingSession session = createSession(false, syncSessionId);

		Transaction noAckTxn = new Transaction(null, false);
		Transaction noMsgTxn = new Transaction(null, false);
		Transaction rmIdsTxn = new Transaction(null, false);

		context.checking(new DbExpectations() {{
			// Add listener
			oneOf(eventBus).addListener(session);
			// Send the protocol versions
			oneOf(recordWriter).writeVersions(with(any(Versions.class)));
			// No acks to send
			oneOf(db).transactionWithNullableResult(with(false),
					withNullableDbCallable(noAckTxn));
			oneOf(db).generateAck(noAckTxn, contactId, MAX_MESSAGE_IDS);
			will(returnValue(null));
			// No messages to send
			oneOf(db).transactionWithNullableResult(with(false),
					withNullableDbCallable(noMsgTxn));
			oneOf(db).generateBatch(noMsgTxn, contactId,
					MAX_RECORD_PAYLOAD_BYTES, MAX_LATENCY);
			will(returnValue(null));
			// Send the end of stream marker
			oneOf(streamWriter).sendEndOfStream();
			// Remove any acked/sent message IDs
			oneOf(db).transaction(with(false), withDbRunnable(rmIdsTxn));
			oneOf(db)
					.setSyncSessionComplete(rmIdsTxn, contactId, syncSessionId);
			// Remove listener
			oneOf(eventBus).removeListener(session);
		}});

		session.run();
	}

	@Test
	public void testSomethingToSend() throws Exception {
		SimplexOutgoingSession session = createSession(false, null);

		Transaction ackTxn = new Transaction(null, false);
		Transaction noAckTxn = new Transaction(null, false);
		Transaction msgTxn = new Transaction(null, false);
		Transaction noMsgTxn = new Transaction(null, false);

		context.checking(new DbExpectations() {{
			// Add listener
			oneOf(eventBus).addListener(session);
			// Send the protocol versions
			oneOf(recordWriter).writeVersions(with(any(Versions.class)));
			// One ack to send
			oneOf(db).transactionWithNullableResult(with(false),
					withNullableDbCallable(ackTxn));
			oneOf(db).generateAck(ackTxn, contactId, MAX_MESSAGE_IDS);
			will(returnValue(ack));
			oneOf(recordWriter).writeAck(ack);
			// No more acks
			oneOf(db).transactionWithNullableResult(with(false),
					withNullableDbCallable(noAckTxn));
			oneOf(db).generateAck(noAckTxn, contactId, MAX_MESSAGE_IDS);
			will(returnValue(null));
			// One message to send
			oneOf(db).transactionWithNullableResult(with(false),
					withNullableDbCallable(msgTxn));
			oneOf(db).generateBatch(msgTxn, contactId,
					MAX_RECORD_PAYLOAD_BYTES, MAX_LATENCY);
			will(returnValue(singletonList(message)));
			oneOf(recordWriter).writeMessage(message);
			// No more messages
			oneOf(db).transactionWithNullableResult(with(false),
					withNullableDbCallable(noMsgTxn));
			oneOf(db).generateBatch(noMsgTxn, contactId,
					MAX_RECORD_PAYLOAD_BYTES, MAX_LATENCY);
			will(returnValue(null));
			// Send the end of stream marker
			oneOf(streamWriter).sendEndOfStream();
			// Remove listener
			oneOf(eventBus).removeListener(session);
		}});

		session.run();
	}

	@Test
	public void testSomethingToSendEagerly() throws Exception {
		SimplexOutgoingSession session = createSession(true, null);

		Map<MessageId, Integer> unacked = new LinkedHashMap<>();
		unacked.put(message.getId(), message.getRawLength());
		unacked.put(message1.getId(), message1.getRawLength());

		Transaction ackTxn = new Transaction(null, false);
		Transaction noAckTxn = new Transaction(null, false);
		Transaction idsTxn = new Transaction(null, true);
		Transaction msgTxn = new Transaction(null, false);
		Transaction msgTxn1 = new Transaction(null, false);

		context.checking(new DbExpectations() {{
			// Add listener
			oneOf(eventBus).addListener(session);
			// Send the protocol versions
			oneOf(recordWriter).writeVersions(with(any(Versions.class)));
			// One ack to send
			oneOf(db).transactionWithNullableResult(with(false),
					withNullableDbCallable(ackTxn));
			oneOf(db).generateAck(ackTxn, contactId, MAX_MESSAGE_IDS);
			will(returnValue(ack));
			oneOf(recordWriter).writeAck(ack);
			// No more acks
			oneOf(db).transactionWithNullableResult(with(false),
					withNullableDbCallable(noAckTxn));
			oneOf(db).generateAck(noAckTxn, contactId, MAX_MESSAGE_IDS);
			will(returnValue(null));
			// Two messages to send
			oneOf(db).transactionWithResult(with(true), withDbCallable(idsTxn));
			oneOf(db).getUnackedMessagesToSend(idsTxn, contactId);
			will(returnValue(unacked));
			// Send the first message
			oneOf(db).transactionWithResult(with(false),
					withDbCallable(msgTxn));
			oneOf(db).generateBatch(msgTxn, contactId,
					singletonList(message.getId()), MAX_LATENCY);
			will(returnValue(singletonList(message)));
			oneOf(recordWriter).writeMessage(message);
			// Send the second message
			oneOf(db).transactionWithResult(with(false),
					withDbCallable(msgTxn1));
			oneOf(db).generateBatch(msgTxn1, contactId,
					singletonList(message1.getId()), MAX_LATENCY);
			will(returnValue(singletonList(message1)));
			oneOf(recordWriter).writeMessage(message1);
			// Send the end of stream marker
			oneOf(streamWriter).sendEndOfStream();
			// Remove listener
			oneOf(eventBus).removeListener(session);
		}});

		session.run();
	}

	@Test
	public void testSomethingToSendWithSyncSessionId() throws Exception {
		SyncSessionId syncSessionId = new SyncSessionId(getRandomId());
		SimplexOutgoingSession session = createSession(false, syncSessionId);

		Transaction ackTxn = new Transaction(null, false);
		Transaction noAckTxn = new Transaction(null, false);
		Transaction msgTxn = new Transaction(null, false);
		Transaction noMsgTxn = new Transaction(null, false);
		Transaction rmIdsTxn = new Transaction(null, false);

		context.checking(new DbExpectations() {{
			// Add listener
			oneOf(eventBus).addListener(session);
			// Send the protocol versions
			oneOf(recordWriter).writeVersions(with(any(Versions.class)));
			// One ack to send
			oneOf(db).transactionWithNullableResult(with(false),
					withNullableDbCallable(ackTxn));
			oneOf(db).generateAck(ackTxn, contactId, MAX_MESSAGE_IDS);
			will(returnValue(ack));
			oneOf(db).addAckedMessageIds(ackTxn, contactId, syncSessionId,
					ack.getMessageIds());
			oneOf(recordWriter).writeAck(ack);
			// No more acks
			oneOf(db).transactionWithNullableResult(with(false),
					withNullableDbCallable(noAckTxn));
			oneOf(db).generateAck(noAckTxn, contactId, MAX_MESSAGE_IDS);
			will(returnValue(null));
			// One message to send
			oneOf(db).transactionWithNullableResult(with(false),
					withNullableDbCallable(msgTxn));
			oneOf(db).generateBatch(msgTxn, contactId,
					MAX_RECORD_PAYLOAD_BYTES, MAX_LATENCY);
			will(returnValue(singletonList(message)));
			oneOf(db).addSentMessageIds(msgTxn, contactId, syncSessionId,
					singletonList(message.getId()));
			oneOf(recordWriter).writeMessage(message);
			// No more messages
			oneOf(db).transactionWithNullableResult(with(false),
					withNullableDbCallable(noMsgTxn));
			oneOf(db).generateBatch(noMsgTxn, contactId,
					MAX_RECORD_PAYLOAD_BYTES, MAX_LATENCY);
			will(returnValue(null));
			// Send the end of stream marker
			oneOf(streamWriter).sendEndOfStream();
			// Remove any acked/sent message IDs
			oneOf(db).transaction(with(false), withDbRunnable(rmIdsTxn));
			oneOf(db)
					.setSyncSessionComplete(rmIdsTxn, contactId, syncSessionId);
			// Remove listener
			oneOf(eventBus).removeListener(session);
		}});

		session.run();
	}

	private SimplexOutgoingSession createSession(boolean eager,
			@Nullable SyncSessionId syncSessionId) {
		return new SimplexOutgoingSession(db, eventBus, contactId, transportId,
				MAX_LATENCY, eager, streamWriter, recordWriter, syncSessionId);
	}
}
