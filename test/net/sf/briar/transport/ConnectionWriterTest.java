package net.sf.briar.transport;

import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PACKET_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MIN_CONNECTION_LENGTH;

import java.io.ByteArrayOutputStream;

import junit.framework.TestCase;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ConnectionWriterTest extends TestCase {

	private final ConnectionWriterFactory connectionWriterFactory;
	private final byte[] secret = new byte[100];
	private final int transportId = 999;
	private final long connection = 1234L;

	public ConnectionWriterTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new CryptoModule(),
				new TransportModule());
		connectionWriterFactory = i.getInstance(ConnectionWriterFactory.class);
	}

	@Test
	public void testOverhead() throws Exception {
		ByteArrayOutputStream out =
			new ByteArrayOutputStream(MIN_CONNECTION_LENGTH);
		ConnectionWriter w = connectionWriterFactory.createConnectionWriter(out,
				MIN_CONNECTION_LENGTH, true, transportId, connection, secret);
		// Check that the connection writer thinks there's room for a packet
		long capacity = w.getCapacity();
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
