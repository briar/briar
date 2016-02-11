package org.briarproject.sync;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.sync.Ack;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.PacketWriter;
import org.briarproject.plugins.ImmediateExecutor;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Executor;

import static org.briarproject.api.sync.SyncConstants.MAX_MESSAGE_IDS;

public class SimplexOutgoingSessionTest extends BriarTestCase {

	private final Mockery context;
	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final EventBus eventBus;
	private final ContactId contactId;
	private final TransportId transportId;
	private final MessageId messageId;
	private final int maxLatency;
	private final PacketWriter packetWriter;

	public SimplexOutgoingSessionTest() {
		context = new Mockery();
		db = context.mock(DatabaseComponent.class);
		dbExecutor = new ImmediateExecutor();
		eventBus = context.mock(EventBus.class);
		packetWriter = context.mock(PacketWriter.class);
		contactId = new ContactId(234);
		transportId = new TransportId("id");
		messageId = new MessageId(TestUtils.getRandomId());
		maxLatency = Integer.MAX_VALUE;
	}

	@Test
	public void testNothingToSend() throws Exception {
		final SimplexOutgoingSession session = new SimplexOutgoingSession(db,
				dbExecutor, eventBus, contactId, transportId, maxLatency,
				packetWriter);
		final Transaction noAckTxn = new Transaction(null);
		final Transaction noMsgTxn = new Transaction(null);
		context.checking(new Expectations() {{
			// Add listener
			oneOf(eventBus).addListener(session);
			// No acks to send
			oneOf(db).startTransaction();
			will(returnValue(noAckTxn));
			oneOf(db).generateAck(noAckTxn, contactId, MAX_MESSAGE_IDS);
			will(returnValue(null));
			oneOf(db).endTransaction(noAckTxn);
			// No messages to send
			oneOf(db).startTransaction();
			will(returnValue(noMsgTxn));
			oneOf(db).generateBatch(with(noMsgTxn), with(contactId),
					with(any(int.class)), with(maxLatency));
			will(returnValue(null));
			oneOf(db).endTransaction(noMsgTxn);
			// Flush the output stream
			oneOf(packetWriter).flush();
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
				dbExecutor, eventBus, contactId, transportId, maxLatency,
				packetWriter);
		final Transaction ackTxn = new Transaction(null);
		final Transaction noAckTxn = new Transaction(null);
		final Transaction msgTxn = new Transaction(null);
		final Transaction noMsgTxn = new Transaction(null);
		context.checking(new Expectations() {{
			// Add listener
			oneOf(eventBus).addListener(session);
			// One ack to send
			oneOf(db).startTransaction();
			will(returnValue(ackTxn));
			oneOf(db).generateAck(ackTxn, contactId, MAX_MESSAGE_IDS);
			will(returnValue(ack));
			oneOf(db).endTransaction(ackTxn);
			oneOf(packetWriter).writeAck(ack);
			// One message to send
			oneOf(db).startTransaction();
			will(returnValue(msgTxn));
			oneOf(db).generateBatch(with(msgTxn), with(contactId),
					with(any(int.class)), with(maxLatency));
			will(returnValue(Arrays.asList(raw)));
			oneOf(db).endTransaction(msgTxn);
			oneOf(packetWriter).writeMessage(raw);
			// No more acks
			oneOf(db).startTransaction();
			will(returnValue(noAckTxn));
			oneOf(db).generateAck(noAckTxn, contactId, MAX_MESSAGE_IDS);
			will(returnValue(null));
			oneOf(db).endTransaction(noAckTxn);
			// No more messages
			oneOf(db).startTransaction();
			will(returnValue(noMsgTxn));
			oneOf(db).generateBatch(with(noMsgTxn), with(contactId),
					with(any(int.class)), with(maxLatency));
			will(returnValue(null));
			oneOf(db).endTransaction(noMsgTxn);
			// Flush the output stream
			oneOf(packetWriter).flush();
			// Remove listener
			oneOf(eventBus).removeListener(session);
		}});
		session.run();
		context.assertIsSatisfied();
	}
}
