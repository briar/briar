package net.sf.briar.protocol.batch;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.ProtocolConstants;
import net.sf.briar.api.protocol.ProtocolWriterFactory;
import net.sf.briar.api.protocol.RawBatch;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.api.transport.TransportConstants;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.protocol.ProtocolModule;
import net.sf.briar.protocol.stream.ProtocolStreamModule;
import net.sf.briar.serial.SerialModule;
import net.sf.briar.transport.TransportModule;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

public class OutgoingBatchConnectionTest extends TestCase {

	private final Mockery context;
	private final DatabaseComponent db;
	private final ConnectionWriterFactory connFactory;
	private final ProtocolWriterFactory protoFactory;
	private final ContactId contactId;
	private final TransportIndex transportIndex;
	private final byte[] secret;

	public OutgoingBatchConnectionTest() {
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
		Injector i = Guice.createInjector(testModule, new CryptoModule(),
				new SerialModule(), new TransportModule(),
				new ProtocolBatchModule(), new ProtocolModule(),
				new ProtocolStreamModule());
		connFactory = i.getInstance(ConnectionWriterFactory.class);
		protoFactory = i.getInstance(ProtocolWriterFactory.class);
		contactId = new ContactId(1);
		transportIndex = new TransportIndex(13);
		secret = new byte[32];
	}

	@Test
	public void testConnectionTooShort() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TestBatchTransportWriter transport = new TestBatchTransportWriter(out,
				ProtocolConstants.MAX_PACKET_LENGTH);
		OutgoingBatchConnection connection = new OutgoingBatchConnection(db,
				connFactory, protoFactory, contactId, transportIndex,
				transport);
		final ConnectionContext ctx = context.mock(ConnectionContext.class);
		context.checking(new Expectations() {{
			oneOf(db).getConnectionContext(contactId, transportIndex);
			will(returnValue(ctx));
			oneOf(ctx).getSecret();
			will(returnValue(secret));
		}});
		connection.write();
		// Nothing should have been written
		assertEquals(0, out.size());
		// The transport should have been disposed with success == false
		assertFalse(transport.getSuccess());
		context.assertIsSatisfied();
	}

	@Test
	public void testNothingToSend() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TestBatchTransportWriter transport = new TestBatchTransportWriter(out,
				TransportConstants.MIN_CONNECTION_LENGTH);
		OutgoingBatchConnection connection = new OutgoingBatchConnection(db,
				connFactory, protoFactory, contactId, transportIndex,
				transport);
		final ConnectionContext ctx = context.mock(ConnectionContext.class);
		context.checking(new Expectations() {{
			oneOf(db).getConnectionContext(contactId, transportIndex);
			will(returnValue(ctx));
			oneOf(ctx).getSecret();
			will(returnValue(secret));
			// No transports to send
			oneOf(db).generateTransportUpdate(contactId);
			will(returnValue(null));
			// No subscriptions to send
			oneOf(db).generateSubscriptionUpdate(contactId);
			will(returnValue(null));
			// No acks to send
			oneOf(db).generateAck(with(contactId), with(any(int.class)));
			will(returnValue(null));
			// No batches to send
			oneOf(db).generateBatch(with(contactId), with(any(int.class)));
			will(returnValue(null));
		}});
		connection.write();
		// Nothing should have been written
		assertEquals(0, out.size());
		// The transport should have been disposed with success == true
		assertTrue(transport.getSuccess());
		context.assertIsSatisfied();
	}

	@Test
	public void testSomethingToSend() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TestBatchTransportWriter transport = new TestBatchTransportWriter(out,
				TransportConstants.MIN_CONNECTION_LENGTH);
		OutgoingBatchConnection connection = new OutgoingBatchConnection(db,
				connFactory, protoFactory, contactId, transportIndex,
				transport);
		final ConnectionContext ctx = context.mock(ConnectionContext.class);
		final Ack ack = context.mock(Ack.class);
		final BatchId batchId = new BatchId(TestUtils.getRandomId());
		final RawBatch batch = context.mock(RawBatch.class);
		final byte[] message = new byte[1234];
		context.checking(new Expectations() {{
			oneOf(db).getConnectionContext(contactId, transportIndex);
			will(returnValue(ctx));
			oneOf(ctx).getSecret();
			will(returnValue(secret));
			// No transports to send
			oneOf(db).generateTransportUpdate(contactId);
			will(returnValue(null));
			// No subscriptions to send
			oneOf(db).generateSubscriptionUpdate(contactId);
			will(returnValue(null));
			// One ack to send
			oneOf(db).generateAck(with(contactId), with(any(int.class)));
			will(returnValue(ack));
			oneOf(ack).getBatchIds();
			will(returnValue(Collections.singletonList(batchId)));
			// No more acks
			oneOf(db).generateAck(with(contactId), with(any(int.class)));
			will(returnValue(null));
			// One batch to send
			oneOf(db).generateBatch(with(contactId), with(any(int.class)));
			will(returnValue(batch));
			oneOf(batch).getMessages();
			will(returnValue(Collections.singletonList(message)));
			// No more batches
			oneOf(db).generateBatch(with(contactId), with(any(int.class)));
			will(returnValue(null));
		}});
		connection.write();
		// Something should have been written
		assertTrue(out.size() > UniqueId.LENGTH + message.length);
		// The transport should have been disposed with success == true
		assertTrue(transport.getSuccess());
		context.assertIsSatisfied();
	}
}
