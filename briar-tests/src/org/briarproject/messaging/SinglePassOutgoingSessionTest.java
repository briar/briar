package org.briarproject.messaging;

import static org.briarproject.api.transport.TransportConstants.HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAC_LENGTH;
import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.briarproject.BriarTestCase;
import org.briarproject.TestLifecycleModule;
import org.briarproject.TestSystemModule;
import org.briarproject.TestUtils;
import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.UniqueId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.messaging.Ack;
import org.briarproject.api.messaging.MessageId;
import org.briarproject.api.messaging.MessagingSession;
import org.briarproject.api.messaging.PacketWriterFactory;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.StreamWriterFactory;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.event.EventModule;
import org.briarproject.serial.SerialModule;
import org.briarproject.transport.TransportModule;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

public class SinglePassOutgoingSessionTest extends BriarTestCase {

	// FIXME: This is an integration test, not a unit test

	private final Mockery context;
	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final StreamWriterFactory streamWriterFactory;
	private final PacketWriterFactory packetWriterFactory;
	private final ContactId contactId;
	private final MessageId messageId;
	private final TransportId transportId;
	private final byte[] secret;

	public SinglePassOutgoingSessionTest() {
		context = new Mockery();
		db = context.mock(DatabaseComponent.class);
		dbExecutor = Executors.newSingleThreadExecutor();
		Module testModule = new AbstractModule() {
			@Override
			public void configure() {
				bind(DatabaseComponent.class).toInstance(db);
				bind(Executor.class).annotatedWith(
						DatabaseExecutor.class).toInstance(dbExecutor);
			}
		};
		Injector i = Guice.createInjector(testModule,
				new TestLifecycleModule(), new TestSystemModule(),
				new CryptoModule(), new EventModule(), new MessagingModule(),
				new SerialModule(), new TransportModule());
		streamWriterFactory = i.getInstance(StreamWriterFactory.class);
		packetWriterFactory = i.getInstance(PacketWriterFactory.class);
		contactId = new ContactId(234);
		messageId = new MessageId(TestUtils.getRandomId());
		transportId = new TransportId("id");
		secret = new byte[32];
		new Random().nextBytes(secret);
	}

	@Test
	public void testNothingToSend() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TestTransportConnectionWriter writer =
				new TestTransportConnectionWriter(out);
		StreamContext ctx = new StreamContext(contactId, transportId,
				secret, 0, true);
		MessagingSession session = new SinglePassOutgoingSession(db, dbExecutor,
				streamWriterFactory, packetWriterFactory, ctx, writer);
		context.checking(new Expectations() {{
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
		}});
		session.run();
		// Only the tag and an empty final frame should have been written
		assertEquals(TAG_LENGTH + HEADER_LENGTH + MAC_LENGTH, out.size());
		context.assertIsSatisfied();
	}

	@Test
	public void testSomethingToSend() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TestTransportConnectionWriter writer =
				new TestTransportConnectionWriter(out);
		StreamContext ctx = new StreamContext(contactId, transportId,
				secret, 0, true);
		MessagingSession session = new SinglePassOutgoingSession(db, dbExecutor,
				streamWriterFactory, packetWriterFactory,
				ctx, writer);
		final byte[] raw = new byte[1234];
		context.checking(new Expectations() {{
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
		}});
		session.run();
		// Something should have been written
		int overhead = TAG_LENGTH + HEADER_LENGTH + MAC_LENGTH;
		assertTrue(out.size() > overhead + UniqueId.LENGTH + raw.length);
		context.assertIsSatisfied();
	}
}
