package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.sync.Ack;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.SyncRecordWriter;
import org.briarproject.bramble.api.transport.StreamWriter;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.ImmediateExecutor;
import org.jmock.Expectations;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Executor;

import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_IDS;
import static org.briarproject.bramble.test.TestUtils.getRandomId;

public class SimplexOutgoingSessionTest extends BrambleMockTestCase {

	private static final int MAX_LATENCY = Integer.MAX_VALUE;

	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final EventBus eventBus = context.mock(EventBus.class);
	private final StreamWriter streamWriter = context.mock(StreamWriter.class);
	private final SyncRecordWriter recordWriter =
			context.mock(SyncRecordWriter.class);

	private final Executor dbExecutor = new ImmediateExecutor();
	private final ContactId contactId = new ContactId(234);
	private final MessageId messageId = new MessageId(getRandomId());

	@Test
	public void testNothingToSend() throws Exception {
		SimplexOutgoingSession session = new SimplexOutgoingSession(db,
				dbExecutor, eventBus, contactId, MAX_LATENCY, streamWriter,
				recordWriter);
		Transaction noAckTxn = new Transaction(null, false);
		Transaction noMsgTxn = new Transaction(null, false);

		context.checking(new Expectations() {{
			// Add listener
			oneOf(eventBus).addListener(session);
			// No acks to send
			oneOf(db).startTransaction(false);
			will(returnValue(noAckTxn));
			oneOf(db).generateAck(noAckTxn, contactId, MAX_MESSAGE_IDS);
			will(returnValue(null));
			oneOf(db).commitTransaction(noAckTxn);
			oneOf(db).endTransaction(noAckTxn);
			// No messages to send
			oneOf(db).startTransaction(false);
			will(returnValue(noMsgTxn));
			oneOf(db).generateBatch(with(noMsgTxn), with(contactId),
					with(any(int.class)), with(MAX_LATENCY));
			will(returnValue(null));
			oneOf(db).commitTransaction(noMsgTxn);
			oneOf(db).endTransaction(noMsgTxn);
			// Send the end of stream marker
			oneOf(streamWriter).sendEndOfStream();
			// Remove listener
			oneOf(eventBus).removeListener(session);
		}});

		session.run();
	}

	@Test
	public void testSomethingToSend() throws Exception {
		Ack ack = new Ack(Collections.singletonList(messageId));
		byte[] raw = new byte[1234];
		SimplexOutgoingSession session = new SimplexOutgoingSession(db,
				dbExecutor, eventBus, contactId, MAX_LATENCY, streamWriter,
				recordWriter);
		Transaction ackTxn = new Transaction(null, false);
		Transaction noAckTxn = new Transaction(null, false);
		Transaction msgTxn = new Transaction(null, false);
		Transaction noMsgTxn = new Transaction(null, false);

		context.checking(new Expectations() {{
			// Add listener
			oneOf(eventBus).addListener(session);
			// One ack to send
			oneOf(db).startTransaction(false);
			will(returnValue(ackTxn));
			oneOf(db).generateAck(ackTxn, contactId, MAX_MESSAGE_IDS);
			will(returnValue(ack));
			oneOf(db).commitTransaction(ackTxn);
			oneOf(db).endTransaction(ackTxn);
			oneOf(recordWriter).writeAck(ack);
			// One message to send
			oneOf(db).startTransaction(false);
			will(returnValue(msgTxn));
			oneOf(db).generateBatch(with(msgTxn), with(contactId),
					with(any(int.class)), with(MAX_LATENCY));
			will(returnValue(Arrays.asList(raw)));
			oneOf(db).commitTransaction(msgTxn);
			oneOf(db).endTransaction(msgTxn);
			oneOf(recordWriter).writeMessage(raw);
			// No more acks
			oneOf(db).startTransaction(false);
			will(returnValue(noAckTxn));
			oneOf(db).generateAck(noAckTxn, contactId, MAX_MESSAGE_IDS);
			will(returnValue(null));
			oneOf(db).commitTransaction(noAckTxn);
			oneOf(db).endTransaction(noAckTxn);
			// No more messages
			oneOf(db).startTransaction(false);
			will(returnValue(noMsgTxn));
			oneOf(db).generateBatch(with(noMsgTxn), with(contactId),
					with(any(int.class)), with(MAX_LATENCY));
			will(returnValue(null));
			oneOf(db).commitTransaction(noMsgTxn);
			oneOf(db).endTransaction(noMsgTxn);
			// Send the end of stream marker
			oneOf(streamWriter).sendEndOfStream();
			// Remove listener
			oneOf(eventBus).removeListener(session);
		}});

		session.run();
	}
}
