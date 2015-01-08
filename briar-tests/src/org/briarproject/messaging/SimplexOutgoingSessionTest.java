package org.briarproject.messaging;

import java.util.Arrays;
import java.util.concurrent.Executor;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.messaging.Ack;
import org.briarproject.api.messaging.MessageId;
import org.briarproject.api.messaging.PacketWriter;
import org.briarproject.plugins.ImmediateExecutor;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

public class SimplexOutgoingSessionTest extends BriarTestCase {

	private static final int MAX_MESSAGES_PER_ACK = 10;

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
		context.checking(new Expectations() {{
			// Add listener
			oneOf(eventBus).addListener(session);
			// No transport acks to send
			oneOf(db).generateTransportAcks(contactId);
			will(returnValue(null));
			// No transport updates to send
			oneOf(db).generateTransportUpdates(contactId, maxLatency);
			will(returnValue(null));
			// No subscription ack to send
			oneOf(db).generateSubscriptionAck(contactId);
			will(returnValue(null));
			// No subscription update to send
			oneOf(db).generateSubscriptionUpdate(contactId, maxLatency);
			will(returnValue(null));
			// No retention ack to send
			oneOf(db).generateRetentionAck(contactId);
			will(returnValue(null));
			// No retention update to send
			oneOf(db).generateRetentionUpdate(contactId, maxLatency);
			will(returnValue(null));
			// No acks to send
			oneOf(packetWriter).getMaxMessagesForAck(with(any(long.class)));
			will(returnValue(MAX_MESSAGES_PER_ACK));
			oneOf(db).generateAck(contactId, MAX_MESSAGES_PER_ACK);
			will(returnValue(null));
			// No messages to send
			oneOf(db).generateBatch(with(contactId), with(any(int.class)),
					with(maxLatency));
			will(returnValue(null));
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
		final Ack ack = new Ack(Arrays.asList(messageId));
		final byte[] raw = new byte[1234];
		final SimplexOutgoingSession session = new SimplexOutgoingSession(db,
				dbExecutor, eventBus, contactId, transportId, maxLatency,
				packetWriter);
		context.checking(new Expectations() {{
			// Add listener
			oneOf(eventBus).addListener(session);
			// No transport acks to send
			oneOf(db).generateTransportAcks(contactId);
			will(returnValue(null));
			// No transport updates to send
			oneOf(db).generateTransportUpdates(contactId, maxLatency);
			will(returnValue(null));
			// No subscription ack to send
			oneOf(db).generateSubscriptionAck(contactId);
			will(returnValue(null));
			// No subscription update to send
			oneOf(db).generateSubscriptionUpdate(contactId, maxLatency);
			will(returnValue(null));
			// No retention ack to send
			oneOf(db).generateRetentionAck(contactId);
			will(returnValue(null));
			// No retention update to send
			oneOf(db).generateRetentionUpdate(contactId, maxLatency);
			will(returnValue(null));
			// One ack to send
			oneOf(packetWriter).getMaxMessagesForAck(with(any(long.class)));
			will(returnValue(MAX_MESSAGES_PER_ACK));
			oneOf(db).generateAck(contactId, MAX_MESSAGES_PER_ACK);
			will(returnValue(ack));
			oneOf(packetWriter).writeAck(ack);
			// No more acks
			oneOf(packetWriter).getMaxMessagesForAck(with(any(long.class)));
			will(returnValue(MAX_MESSAGES_PER_ACK));
			oneOf(db).generateAck(contactId, MAX_MESSAGES_PER_ACK);
			will(returnValue(null));
			// One message to send
			oneOf(db).generateBatch(with(contactId), with(any(int.class)),
					with(maxLatency));
			will(returnValue(Arrays.asList(raw)));
			oneOf(packetWriter).writeMessage(raw);
			// No more messages
			oneOf(db).generateBatch(with(contactId), with(any(int.class)),
					with(maxLatency));
			will(returnValue(null));
			// Flush the output stream
			oneOf(packetWriter).flush();
			// Remove listener
			oneOf(eventBus).removeListener(session);
		}});
		session.run();
		context.assertIsSatisfied();
	}
}
