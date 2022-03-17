package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.sync.Ack;
import org.briarproject.bramble.api.sync.DeferredSendHandler;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.SyncRecordWriter;
import org.briarproject.bramble.api.sync.Versions;
import org.briarproject.bramble.api.transport.StreamWriter;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.DbExpectations;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.briarproject.bramble.api.mailbox.MailboxConstants.MAX_FILE_PAYLOAD_BYTES;
import static org.briarproject.bramble.api.record.Record.RECORD_HEADER_BYTES;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_IDS;
import static org.briarproject.bramble.test.TestUtils.getContactId;
import static org.briarproject.bramble.test.TestUtils.getMessage;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.getTransportId;

public class MailboxOutgoingSessionTest extends BrambleMockTestCase {

	private static final int MAX_LATENCY = Integer.MAX_VALUE;

	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final EventBus eventBus = context.mock(EventBus.class);
	private final StreamWriter streamWriter = context.mock(StreamWriter.class);
	private final SyncRecordWriter recordWriter =
			context.mock(SyncRecordWriter.class);
	private final DeferredSendHandler deferredSendHandler =
			context.mock(DeferredSendHandler.class);

	private final ContactId contactId = getContactId();
	private final TransportId transportId = getTransportId();
	private final Message message = getMessage(new GroupId(getRandomId()),
			MAX_MESSAGE_BODY_LENGTH);
	private final Message message1 = getMessage(new GroupId(getRandomId()),
			MAX_MESSAGE_BODY_LENGTH);
	private final int versionRecordBytes = RECORD_HEADER_BYTES + 1;

	@Test
	public void testNothingToSend() throws Exception {
		MailboxOutgoingSession session = new MailboxOutgoingSession(db,
				eventBus, contactId, transportId, MAX_LATENCY,
				streamWriter, recordWriter, deferredSendHandler,
				MAX_FILE_PAYLOAD_BYTES);

		Transaction noAckIdTxn = new Transaction(null, true);
		Transaction noMsgIdTxn = new Transaction(null, true);

		int capacityForMessages = MAX_FILE_PAYLOAD_BYTES - versionRecordBytes;

		context.checking(new DbExpectations() {{
			// Add listener
			oneOf(eventBus).addListener(session);
			// Send the protocol versions
			oneOf(recordWriter).writeVersions(with(any(Versions.class)));
			// Calculate capacity for acks
			oneOf(recordWriter).getBytesWritten();
			will(returnValue((long) versionRecordBytes));
			// No messages to ack
			oneOf(db).transactionWithResult(with(true),
					withDbCallable(noAckIdTxn));
			oneOf(db).getMessagesToAck(noAckIdTxn, contactId, MAX_MESSAGE_IDS);
			will(returnValue(emptyList()));
			// Calculate capacity for messages
			oneOf(recordWriter).getBytesWritten();
			will(returnValue((long) versionRecordBytes));
			// No messages to send
			oneOf(db).transactionWithResult(with(true),
					withDbCallable(noMsgIdTxn));
			oneOf(db).getMessagesToSend(noMsgIdTxn, contactId,
					capacityForMessages, MAX_LATENCY);
			will(returnValue(emptyList()));
			// Send the end of stream marker
			oneOf(streamWriter).sendEndOfStream();
			// Remove listener
			oneOf(eventBus).removeListener(session);
		}});

		session.run();
	}

	@Test
	public void testSomethingToSend() throws Exception {
		MailboxOutgoingSession session = new MailboxOutgoingSession(db,
				eventBus, contactId, transportId, MAX_LATENCY,
				streamWriter, recordWriter, deferredSendHandler,
				MAX_FILE_PAYLOAD_BYTES);

		Transaction ackIdTxn = new Transaction(null, true);
		Transaction noAckIdTxn = new Transaction(null, true);
		Transaction msgIdTxn = new Transaction(null, true);
		Transaction msgTxn = new Transaction(null, true);

		int ackRecordBytes = RECORD_HEADER_BYTES + MessageId.LENGTH;
		int capacityForMessages =
				MAX_FILE_PAYLOAD_BYTES - versionRecordBytes - ackRecordBytes;

		context.checking(new DbExpectations() {{
			// Add listener
			oneOf(eventBus).addListener(session);
			// Send the protocol versions
			oneOf(recordWriter).writeVersions(with(any(Versions.class)));
			// Calculate capacity for acks
			oneOf(recordWriter).getBytesWritten();
			will(returnValue((long) versionRecordBytes));
			// One message to ack
			oneOf(db).transactionWithResult(with(true),
					withDbCallable(ackIdTxn));
			oneOf(db).getMessagesToAck(ackIdTxn, contactId, MAX_MESSAGE_IDS);
			will(returnValue(singletonList(message.getId())));
			// Send the ack
			oneOf(recordWriter).getBytesWritten();
			will(returnValue((long) versionRecordBytes));
			oneOf(recordWriter).writeAck(with(any(Ack.class)));
			oneOf(deferredSendHandler)
					.onAckSent(singletonList(message.getId()));
			// No more messages to ack
			oneOf(db).transactionWithResult(with(true),
					withDbCallable(noAckIdTxn));
			oneOf(db).getMessagesToAck(noAckIdTxn, contactId, MAX_MESSAGE_IDS);
			will(returnValue(emptyList()));
			// Calculate capacity for messages
			oneOf(recordWriter).getBytesWritten();
			will(returnValue((long) versionRecordBytes + ackRecordBytes));
			// One message to send
			oneOf(db).transactionWithResult(with(true),
					withDbCallable(msgIdTxn));
			oneOf(db).getMessagesToSend(msgIdTxn, contactId,
					capacityForMessages, MAX_LATENCY);
			will(returnValue(singletonList(message1.getId())));
			// Send the message
			oneOf(db).transactionWithNullableResult(with(true),
					withNullableDbCallable(msgTxn));
			oneOf(db).getMessageToSend(msgTxn, contactId, message1.getId(),
					MAX_LATENCY, false);
			will(returnValue(message1));
			oneOf(recordWriter).writeMessage(message1);
			oneOf(deferredSendHandler).onMessageSent(message1.getId());
			// Send the end of stream marker
			oneOf(streamWriter).sendEndOfStream();
			// Remove listener
			oneOf(eventBus).removeListener(session);
		}});

		session.run();
	}

	@Test
	public void testAllCapacityUsedByAcks() throws Exception {
		// The file has enough capacity for a max-size ack record, another
		// ack record with one message ID, and a few bytes left over
		int capacity = RECORD_HEADER_BYTES + MessageId.LENGTH * MAX_MESSAGE_IDS
				+ RECORD_HEADER_BYTES + MessageId.LENGTH + MessageId.LENGTH - 1;

		MailboxOutgoingSession session = new MailboxOutgoingSession(db,
				eventBus, contactId, transportId, MAX_LATENCY,
				streamWriter, recordWriter, deferredSendHandler, capacity);

		Transaction ackIdTxn1 = new Transaction(null, true);
		Transaction ackIdTxn2 = new Transaction(null, true);

		int firstAckRecordBytes =
				RECORD_HEADER_BYTES + MessageId.LENGTH * MAX_MESSAGE_IDS;
		int secondAckRecordBytes = RECORD_HEADER_BYTES + MessageId.LENGTH;

		List<MessageId> idsInFirstAck = new ArrayList<>(MAX_MESSAGE_IDS);
		for (int i = 0; i < MAX_MESSAGE_IDS; i++) {
			idsInFirstAck.add(new MessageId(getRandomId()));
		}
		List<MessageId> idsInSecondAck =
				singletonList(new MessageId(getRandomId()));

		context.checking(new DbExpectations() {{
			// Add listener
			oneOf(eventBus).addListener(session);
			// Send the protocol versions
			oneOf(recordWriter).writeVersions(with(any(Versions.class)));
			// Calculate capacity for acks
			oneOf(recordWriter).getBytesWritten();
			will(returnValue((long) versionRecordBytes));
			// Load the IDs for the first ack record
			oneOf(db).transactionWithResult(with(true),
					withDbCallable(ackIdTxn1));
			oneOf(db).getMessagesToAck(ackIdTxn1, contactId, MAX_MESSAGE_IDS);
			will(returnValue(idsInFirstAck));
			// Send the first ack record
			oneOf(recordWriter).writeAck(with(any(Ack.class)));
			oneOf(deferredSendHandler).onAckSent(idsInFirstAck);
			// Calculate remaining capacity for acks
			oneOf(recordWriter).getBytesWritten();
			will(returnValue((long) versionRecordBytes + firstAckRecordBytes));
			// Load the IDs for the second ack record
			oneOf(db).transactionWithResult(with(true),
					withDbCallable(ackIdTxn2));
			oneOf(db).getMessagesToAck(ackIdTxn2, contactId, 1);
			will(returnValue(idsInSecondAck));
			// Send the second ack record
			oneOf(recordWriter).writeAck(with(any(Ack.class)));
			oneOf(deferredSendHandler).onAckSent(idsInSecondAck);
			// Not enough capacity left for another ack
			oneOf(recordWriter).getBytesWritten();
			will(returnValue((long) versionRecordBytes + firstAckRecordBytes
					+ secondAckRecordBytes));
			// Not enough capacity left for any messages
			oneOf(recordWriter).getBytesWritten();
			will(returnValue((long) versionRecordBytes + firstAckRecordBytes
					+ secondAckRecordBytes));
			// Send the end of stream marker
			oneOf(streamWriter).sendEndOfStream();
			// Remove listener
			oneOf(eventBus).removeListener(session);
		}});

		session.run();
	}
}
