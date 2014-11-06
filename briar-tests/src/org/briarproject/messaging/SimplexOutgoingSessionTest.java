package org.briarproject.messaging;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Executor;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.UniqueId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.messaging.Ack;
import org.briarproject.api.messaging.MessageId;
import org.briarproject.api.messaging.PacketWriterFactory;
import org.briarproject.plugins.ImmediateExecutor;
import org.briarproject.serial.SerialModule;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

public class SimplexOutgoingSessionTest extends BriarTestCase {

	// FIXME: This is an integration test, not a unit test

	private final Mockery context;
	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final EventBus eventBus;
	private final PacketWriterFactory packetWriterFactory;
	private final ContactId contactId;
	private final TransportId transportId;
	private final MessageId messageId;
	private final byte[] secret;

	public SimplexOutgoingSessionTest() {
		context = new Mockery();
		db = context.mock(DatabaseComponent.class);
		dbExecutor = new ImmediateExecutor();
		Module testModule = new AbstractModule() {
			@Override
			public void configure() {
				bind(PacketWriterFactory.class).to(
						PacketWriterFactoryImpl.class);
			}
		};
		Injector i = Guice.createInjector(testModule, new SerialModule());
		eventBus = context.mock(EventBus.class);
		packetWriterFactory = i.getInstance(PacketWriterFactory.class);
		contactId = new ContactId(234);
		transportId = new TransportId("id");
		messageId = new MessageId(TestUtils.getRandomId());
		secret = new byte[32];
		new Random().nextBytes(secret);
	}

	@Test
	public void testNothingToSend() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		final SimplexOutgoingSession session = new SimplexOutgoingSession(db,
				dbExecutor, eventBus, packetWriterFactory, contactId,
				transportId, Long.MAX_VALUE, out);
		context.checking(new Expectations() {{
			// Add listener
			oneOf(eventBus).addListener(session);
			// No transport acks to send
			oneOf(db).generateTransportAcks(contactId);
			will(returnValue(null));
			// No transport updates to send
			oneOf(db).generateTransportUpdates(with(contactId),
					with(any(long.class)));
			will(returnValue(null));
			// No subscription ack to send
			oneOf(db).generateSubscriptionAck(contactId);
			will(returnValue(null));
			// No subscription update to send
			oneOf(db).generateSubscriptionUpdate(with(contactId),
					with(any(long.class)));
			will(returnValue(null));
			// No retention ack to send
			oneOf(db).generateRetentionAck(contactId);
			will(returnValue(null));
			// No retention update to send
			oneOf(db).generateRetentionUpdate(with(contactId),
					with(any(long.class)));
			will(returnValue(null));
			// No acks to send
			oneOf(db).generateAck(with(contactId), with(any(int.class)));
			will(returnValue(null));
			// No messages to send
			oneOf(db).generateBatch(with(contactId), with(any(int.class)),
					with(any(long.class)));
			will(returnValue(null));
			// Remove listener
			oneOf(eventBus).removeListener(session);
		}});
		session.run();
		// Nothing should have been written
		assertEquals(0, out.size());
		context.assertIsSatisfied();
	}

	@Test
	public void testSomethingToSend() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		final SimplexOutgoingSession session = new SimplexOutgoingSession(db,
				dbExecutor, eventBus, packetWriterFactory, contactId,
				transportId, Long.MAX_VALUE, out);
		final byte[] raw = new byte[1234];
		context.checking(new Expectations() {{
			// Add listener
			oneOf(eventBus).addListener(session);
			// No transport acks to send
			oneOf(db).generateTransportAcks(contactId);
			will(returnValue(null));
			// No transport updates to send
			oneOf(db).generateTransportUpdates(with(contactId),
					with(any(long.class)));
			will(returnValue(null));
			// No subscription ack to send
			oneOf(db).generateSubscriptionAck(contactId);
			will(returnValue(null));
			// No subscription update to send
			oneOf(db).generateSubscriptionUpdate(with(contactId),
					with(any(long.class)));
			will(returnValue(null));
			// No retention ack to send
			oneOf(db).generateRetentionAck(contactId);
			will(returnValue(null));
			// No retention update to send
			oneOf(db).generateRetentionUpdate(with(contactId),
					with(any(long.class)));
			will(returnValue(null));
			// One ack to send
			oneOf(db).generateAck(with(contactId), with(any(int.class)));
			will(returnValue(new Ack(Arrays.asList(messageId))));
			// No more acks
			oneOf(db).generateAck(with(contactId), with(any(int.class)));
			will(returnValue(null));
			// One message to send
			oneOf(db).generateBatch(with(contactId), with(any(int.class)),
					with(any(long.class)));
			will(returnValue(Arrays.asList(raw)));
			// No more messages
			oneOf(db).generateBatch(with(contactId), with(any(int.class)),
					with(any(long.class)));
			will(returnValue(null));
			// Remove listener
			oneOf(eventBus).removeListener(session);
		}});
		session.run();
		// Something should have been written
		assertTrue(out.size() > UniqueId.LENGTH + raw.length);
		context.assertIsSatisfied();
	}
}
