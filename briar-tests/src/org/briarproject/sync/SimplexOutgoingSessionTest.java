package org.briarproject.sync;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseComponent;
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
		final org.briarproject.sync.SimplexOutgoingSession
				session = new org.briarproject.sync.SimplexOutgoingSession(db,
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
		final Ack ack = new Ack(Collections.singletonList(messageId));
		final byte[] raw = new byte[1234];
		final org.briarproject.sync.SimplexOutgoingSession
				session = new org.briarproject.sync.SimplexOutgoingSession(db,
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
