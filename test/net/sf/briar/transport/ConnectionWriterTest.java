package net.sf.briar.transport;

import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PACKET_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MIN_CONNECTION_LENGTH;

import java.io.ByteArrayOutputStream;
import java.util.Random;

import junit.framework.TestCase;
import net.sf.briar.TestDatabaseModule;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.db.DatabaseModule;
import net.sf.briar.lifecycle.LifecycleModule;
import net.sf.briar.protocol.ProtocolModule;
import net.sf.briar.protocol.batch.ProtocolBatchModule;
import net.sf.briar.protocol.stream.ProtocolStreamModule;
import net.sf.briar.serial.SerialModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ConnectionWriterTest extends TestCase {

	private final ConnectionWriterFactory connectionWriterFactory;
	private final byte[] secret;

	public ConnectionWriterTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new CryptoModule(),
				new DatabaseModule(), new LifecycleModule(),
				new ProtocolModule(), new SerialModule(),
				new TestDatabaseModule(), new ProtocolBatchModule(),
				new TransportModule(), new ProtocolStreamModule());
		connectionWriterFactory = i.getInstance(ConnectionWriterFactory.class);
		secret = new byte[32];
		new Random().nextBytes(secret);
	}

	@Test
	public void testOverhead() throws Exception {
		ByteArrayOutputStream out =
			new ByteArrayOutputStream(MIN_CONNECTION_LENGTH);
		ConnectionWriter w = connectionWriterFactory.createConnectionWriter(out,
				MIN_CONNECTION_LENGTH, secret);
		// Check that the connection writer thinks there's room for a packet
		long capacity = w.getRemainingCapacity();
		assertTrue(capacity >= MAX_PACKET_LENGTH);
		assertTrue(capacity <= MIN_CONNECTION_LENGTH);
		// Check that there really is room for a packet
		byte[] payload = new byte[MAX_PACKET_LENGTH];
		w.getOutputStream().write(payload);
		w.getOutputStream().flush();
		long used = out.size();
		assertTrue(used >= MAX_PACKET_LENGTH);
		assertTrue(used <= MIN_CONNECTION_LENGTH);
	}
}
