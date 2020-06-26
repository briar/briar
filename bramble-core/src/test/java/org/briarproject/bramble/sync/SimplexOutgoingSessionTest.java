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
import org.briarproject.bramble.api.sync.Versions;
import org.briarproject.bramble.api.transport.StreamWriter;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.DbExpectations;
import org.briarproject.bramble.test.ImmediateExecutor;
import org.junit.Test;

import java.util.concurrent.Executor;

import static java.util.Collections.singletonList;
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

	private final Executor dbExecutor = new ImmediateExecutor();
	private final ContactId contactId = getContactId();
	private final TransportId transportId = getTransportId();
	private final Message message = getMessage(new GroupId(getRandomId()));
	private final MessageId messageId = message.getId();

	@Test
	public void testNothingToSend() throws Exception {
		SimplexOutgoingSession session = new SimplexOutgoingSession(db,
				dbExecutor, eventBus, contactId, transportId, MAX_LATENCY,
				streamWriter, recordWriter);
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
			oneOf(db).generateBatch(with(noMsgTxn), with(contactId),
					with(any(int.class)), with(MAX_LATENCY));
			will(returnValue(null));
			// Send the end of stream marker
			oneOf(streamWriter).sendEndOfStream();
			// Remove listener
			oneOf(eventBus).removeListener(session);
		}});

		session.run();
	}

	@Test
	public void testSomethingToSend() throws Exception {
		Ack ack = new Ack(singletonList(messageId));
		SimplexOutgoingSession session = new SimplexOutgoingSession(db,
				dbExecutor, eventBus, contactId, transportId, MAX_LATENCY,
				streamWriter, recordWriter);
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
			// One message to send
			oneOf(db).transactionWithNullableResult(with(false),
					withNullableDbCallable(msgTxn));
			oneOf(db).generateBatch(with(msgTxn), with(contactId),
					with(any(int.class)), with(MAX_LATENCY));
			will(returnValue(singletonList(message)));
			oneOf(recordWriter).writeMessage(message);
			// No more acks
			oneOf(db).transactionWithNullableResult(with(false),
					withNullableDbCallable(noAckTxn));
			oneOf(db).generateAck(noAckTxn, contactId, MAX_MESSAGE_IDS);
			will(returnValue(null));
			// No more messages
			oneOf(db).transactionWithNullableResult(with(false),
					withNullableDbCallable(noMsgTxn));
			oneOf(db).generateBatch(with(noMsgTxn), with(contactId),
					with(any(int.class)), with(MAX_LATENCY));
			will(returnValue(null));
			// Send the end of stream marker
			oneOf(streamWriter).sendEndOfStream();
			// Remove listener
			oneOf(eventBus).removeListener(session);
		}});

		session.run();
	}
}
