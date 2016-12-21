package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.sync.Ack;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.ImmediateExecutor;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.bramble.api.sync.RecordWriter;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Executor;

import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_IDS;

public class SimplexOutgoingSessionTest extends BrambleTestCase {

	private final Mockery context;
	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final EventBus eventBus;
	private final ContactId contactId;
	private final MessageId messageId;
	private final int maxLatency;
	private final RecordWriter recordWriter;

	public SimplexOutgoingSessionTest() {
		context = new Mockery();
		db = context.mock(DatabaseComponent.class);
		dbExecutor = new ImmediateExecutor();
		eventBus = context.mock(EventBus.class);
		recordWriter = context.mock(RecordWriter.class);
		contactId = new ContactId(234);
		messageId = new MessageId(TestUtils.getRandomId());
		maxLatency = Integer.MAX_VALUE;
	}

	@Test
	public void testNothingToSend() throws Exception {
		final SimplexOutgoingSession session = new SimplexOutgoingSession(db,
				dbExecutor, eventBus, contactId, maxLatency, recordWriter);
		final Transaction noAckTxn = new Transaction(null, false);
		final Transaction noMsgTxn = new Transaction(null, false);

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
					with(any(int.class)), with(maxLatency));
			will(returnValue(null));
			oneOf(db).commitTransaction(noMsgTxn);
			oneOf(db).endTransaction(noMsgTxn);
			// Flush the output stream
			oneOf(recordWriter).flush();
			// Remove listener
			oneOf(eventBus).removeListener(session);
		}});

		session.run();

		context.assertIsSatisfied();
	}

	@Test
	public void testSomethingToSend() throws Exception {
		final Ack ack = new Ack(Collections.singletonList(messageId));
		final byte[] raw = new byte[1234];
		final SimplexOutgoingSession session = new SimplexOutgoingSession(db,
				dbExecutor, eventBus, contactId, maxLatency, recordWriter);
		final Transaction ackTxn = new Transaction(null, false);
		final Transaction noAckTxn = new Transaction(null, false);
		final Transaction msgTxn = new Transaction(null, false);
		final Transaction noMsgTxn = new Transaction(null, false);

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
					with(any(int.class)), with(maxLatency));
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
					with(any(int.class)), with(maxLatency));
			will(returnValue(null));
			oneOf(db).commitTransaction(noMsgTxn);
			oneOf(db).endTransaction(noMsgTxn);
			// Flush the output stream
			oneOf(recordWriter).flush();
			// Remove listener
			oneOf(eventBus).removeListener(session);
		}});

		session.run();

		context.assertIsSatisfied();
	}
}
