package net.sf.briar.messaging.simplex;

import static net.sf.briar.api.messaging.MessagingConstants.MAX_PACKET_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MIN_CONNECTION_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import net.sf.briar.BriarTestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.messaging.Ack;
import net.sf.briar.api.messaging.MessageId;
import net.sf.briar.api.messaging.PacketWriterFactory;
import net.sf.briar.api.messaging.TransportId;
import net.sf.briar.api.messaging.UniqueId;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionRegistry;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.clock.ClockModule;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.messaging.MessagingModule;
import net.sf.briar.messaging.duplex.DuplexMessagingModule;
import net.sf.briar.messaging.simplex.OutgoingSimplexConnection;
import net.sf.briar.messaging.simplex.SimplexMessagingModule;
import net.sf.briar.serial.SerialModule;
import net.sf.briar.transport.TransportModule;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

public class OutgoingSimplexConnectionTest extends BriarTestCase {

	// FIXME: This is an integration test, not a unit test

	private final Mockery context;
	private final DatabaseComponent db;
	private final ConnectionRegistry connRegistry;
	private final ConnectionWriterFactory connFactory;
	private final PacketWriterFactory protoFactory;
	private final ContactId contactId;
	private final MessageId messageId;
	private final TransportId transportId;
	private final byte[] secret;

	public OutgoingSimplexConnectionTest() {
		super();
		context = new Mockery();
		db = context.mock(DatabaseComponent.class);
		Module testModule = new AbstractModule() {
			@Override
			public void configure() {
				bind(DatabaseComponent.class).toInstance(db);
				bind(Executor.class).annotatedWith(
						DatabaseExecutor.class).toInstance(
								Executors.newCachedThreadPool());
			}
		};
		Injector i = Guice.createInjector(testModule, new ClockModule(),
				new CryptoModule(), new SerialModule(), new TransportModule(),
				new SimplexMessagingModule(), new MessagingModule(),
				new DuplexMessagingModule());
		connRegistry = i.getInstance(ConnectionRegistry.class);
		connFactory = i.getInstance(ConnectionWriterFactory.class);
		protoFactory = i.getInstance(PacketWriterFactory.class);
		contactId = new ContactId(234);
		messageId = new MessageId(TestUtils.getRandomId());
		transportId = new TransportId(TestUtils.getRandomId());
		secret = new byte[32];
	}

	@Test
	public void testConnectionTooShort() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TestSimplexTransportWriter transport = new TestSimplexTransportWriter(
				out, MAX_PACKET_LENGTH, true);
		ConnectionContext ctx = new ConnectionContext(contactId, transportId,
				secret, 0L, true);
		OutgoingSimplexConnection connection = new OutgoingSimplexConnection(db,
				connRegistry, connFactory, protoFactory, ctx, transport);
		connection.write();
		// Nothing should have been written
		assertEquals(0, out.size());
		// The transport should have been disposed with exception == true
		assertTrue(transport.getDisposed());
		assertTrue(transport.getException());
	}

	@Test
	public void testNothingToSend() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TestSimplexTransportWriter transport = new TestSimplexTransportWriter(
				out, MIN_CONNECTION_LENGTH, true);
		ConnectionContext ctx = new ConnectionContext(contactId, transportId,
				secret, 0L, true);
		OutgoingSimplexConnection connection = new OutgoingSimplexConnection(db,
				connRegistry, connFactory, protoFactory, ctx, transport);
		context.checking(new Expectations() {{
			// No transport acks to send
			oneOf(db).generateTransportAcks(contactId);
			will(returnValue(null));
			// No transport updates to send
			oneOf(db).generateTransportUpdates(contactId);
			will(returnValue(null));
			// No subscription ack to send
			oneOf(db).generateSubscriptionAck(contactId);
			will(returnValue(null));
			// No subscription update to send
			oneOf(db).generateSubscriptionUpdate(contactId);
			will(returnValue(null));
			// No retention ack to send
			oneOf(db).generateRetentionAck(contactId);
			will(returnValue(null));
			// No retention update to send
			oneOf(db).generateRetentionUpdate(contactId);
			will(returnValue(null));
			// No acks to send
			oneOf(db).generateAck(with(contactId), with(any(int.class)));
			will(returnValue(null));
			// No messages to send
			oneOf(db).generateBatch(with(contactId), with(any(int.class)));
			will(returnValue(null));
		}});
		connection.write();
		// Nothing should have been written
		assertEquals(0, out.size());
		// The transport should have been disposed with exception == false
		assertTrue(transport.getDisposed());
		assertFalse(transport.getException());
		context.assertIsSatisfied();
	}

	@Test
	public void testSomethingToSend() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TestSimplexTransportWriter transport = new TestSimplexTransportWriter(
				out, MIN_CONNECTION_LENGTH, true);
		ConnectionContext ctx = new ConnectionContext(contactId, transportId,
				secret, 0L, true);
		OutgoingSimplexConnection connection = new OutgoingSimplexConnection(db,
				connRegistry, connFactory, protoFactory, ctx, transport);
		final byte[] raw = new byte[1234];
		context.checking(new Expectations() {{
			// No transport acks to send
			oneOf(db).generateTransportAcks(contactId);
			will(returnValue(null));
			// No transport updates to send
			oneOf(db).generateTransportUpdates(contactId);
			will(returnValue(null));
			// No subscription ack to send
			oneOf(db).generateSubscriptionAck(contactId);
			will(returnValue(null));
			// No subscription update to send
			oneOf(db).generateSubscriptionUpdate(contactId);
			will(returnValue(null));
			// No retention ack to send
			oneOf(db).generateRetentionAck(contactId);
			will(returnValue(null));
			// No retention update to send
			oneOf(db).generateRetentionUpdate(contactId);
			will(returnValue(null));
			// One ack to send
			oneOf(db).generateAck(with(contactId), with(any(int.class)));
			will(returnValue(new Ack(Arrays.asList(messageId))));
			// No more acks
			oneOf(db).generateAck(with(contactId), with(any(int.class)));
			will(returnValue(null));
			// One message to send
			oneOf(db).generateBatch(with(contactId), with(any(int.class)));
			will(returnValue(Collections.singletonList(raw)));
			// No more messages
			oneOf(db).generateBatch(with(contactId), with(any(int.class)));
			will(returnValue(null));
		}});
		connection.write();
		// Something should have been written
		int overhead = TAG_LENGTH + HEADER_LENGTH + MAC_LENGTH;
		assertTrue(out.size() > overhead + UniqueId.LENGTH + raw.length);
		// The transport should have been disposed with exception == false
		assertTrue(transport.getDisposed());
		assertFalse(transport.getException());
		context.assertIsSatisfied();
	}
}
