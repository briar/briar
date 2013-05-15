package net.sf.briar.transport;

import static net.sf.briar.api.messaging.MessagingConstants.MAX_PACKET_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MIN_CONNECTION_LENGTH;
import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

import net.sf.briar.BriarTestCase;
import net.sf.briar.TestLifecycleModule;
import net.sf.briar.TestUtils;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.crypto.AuthenticatedCipher;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

public class TransportIntegrationTest extends BriarTestCase {

	private final int FRAME_LENGTH = 2048;

	private final CryptoComponent crypto;
	private final ConnectionWriterFactory connectionWriterFactory;
	private final ContactId contactId;
	private final TransportId transportId;
	private final AuthenticatedCipher frameCipher;
	private final Random random;
	private final byte[] secret;
	private final ErasableKey frameKey;

	public TransportIntegrationTest() {
		Module testModule = new AbstractModule() {
			public void configure() {
				bind(ConnectionWriterFactory.class).to(
						ConnectionWriterFactoryImpl.class);
			}
		};
		Injector i = Guice.createInjector(testModule, new CryptoModule(),
				new TestLifecycleModule());
		crypto = i.getInstance(CryptoComponent.class);
		connectionWriterFactory = i.getInstance(ConnectionWriterFactory.class);
		contactId = new ContactId(234);
		transportId = new TransportId(TestUtils.getRandomId());
		frameCipher = crypto.getFrameCipher();
		random = new Random();
		// Since we're sending frames to ourselves, we only need outgoing keys
		secret = new byte[32];
		random.nextBytes(secret);
		frameKey = crypto.deriveFrameKey(secret, 0, true, true);
	}

	@Test
	public void testInitiatorWriteAndRead() throws Exception {
		testWriteAndRead(true);
	}

	@Test
	public void testResponderWriteAndRead() throws Exception {
		testWriteAndRead(false);
	}

	private void testWriteAndRead(boolean initiator) throws Exception {
		// Generate two random frames
		byte[] frame = new byte[1234];
		random.nextBytes(frame);
		byte[] frame1 = new byte[321];
		random.nextBytes(frame1);
		// Copy the frame key - the copy will be erased
		ErasableKey frameCopy = frameKey.copy();
		// Write the frames
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		FrameWriter encryptionOut = new OutgoingEncryptionLayer(out,
				Long.MAX_VALUE, frameCipher, frameCopy, FRAME_LENGTH);
		ConnectionWriterImpl writer = new ConnectionWriterImpl(encryptionOut,
				FRAME_LENGTH);
		OutputStream out1 = writer.getOutputStream();
		out1.write(frame);
		out1.flush();
		out1.write(frame1);
		out1.flush();
		byte[] output = out.toByteArray();
		assertEquals(FRAME_LENGTH * 2, output.length);
		// Read the tag and the frames back
		ByteArrayInputStream in = new ByteArrayInputStream(output);
		FrameReader encryptionIn = new IncomingEncryptionLayer(in, frameCipher,
				frameKey, FRAME_LENGTH);
		ConnectionReaderImpl reader = new ConnectionReaderImpl(encryptionIn,
				FRAME_LENGTH);
		InputStream in1 = reader.getInputStream();
		byte[] recovered = new byte[frame.length];
		int offset = 0;
		while(offset < recovered.length) {
			int read = in1.read(recovered, offset, recovered.length - offset);
			if(read == -1) break;
			offset += read;
		}
		assertEquals(recovered.length, offset);
		assertArrayEquals(frame, recovered);
		byte[] recovered1 = new byte[frame1.length];
		offset = 0;
		while(offset < recovered1.length) {
			int read = in1.read(recovered1, offset, recovered1.length - offset);
			if(read == -1) break;
			offset += read;
		}
		assertEquals(recovered1.length, offset);
		assertArrayEquals(frame1, recovered1);
	}

	@Test
	public void testOverheadWithTag() throws Exception {
		ByteArrayOutputStream out =
				new ByteArrayOutputStream(MIN_CONNECTION_LENGTH);
		ConnectionContext ctx = new ConnectionContext(contactId, transportId,
				secret, 0, true);
		ConnectionWriter w = connectionWriterFactory.createConnectionWriter(out,
				MIN_CONNECTION_LENGTH, ctx, false, true);
		// Check that the connection writer thinks there's room for a packet
		long capacity = w.getRemainingCapacity();
		assertTrue(capacity > MAX_PACKET_LENGTH);
		assertTrue(capacity < MIN_CONNECTION_LENGTH);
		// Check that there really is room for a packet
		byte[] payload = new byte[MAX_PACKET_LENGTH];
		w.getOutputStream().write(payload);
		w.getOutputStream().close();
		long used = out.size();
		assertTrue(used > MAX_PACKET_LENGTH);
		assertTrue(used <= MIN_CONNECTION_LENGTH);
	}

	@Test
	public void testOverheadWithoutTag() throws Exception {
		ByteArrayOutputStream out =
				new ByteArrayOutputStream(MIN_CONNECTION_LENGTH);
		ConnectionContext ctx = new ConnectionContext(contactId, transportId,
				secret, 0, true);
		ConnectionWriter w = connectionWriterFactory.createConnectionWriter(out,
				MIN_CONNECTION_LENGTH, ctx, false, false);
		// Check that the connection writer thinks there's room for a packet
		long capacity = w.getRemainingCapacity();
		assertTrue(capacity > MAX_PACKET_LENGTH);
		assertTrue(capacity < MIN_CONNECTION_LENGTH);
		// Check that there really is room for a packet
		byte[] payload = new byte[MAX_PACKET_LENGTH];
		w.getOutputStream().write(payload);
		w.getOutputStream().close();
		long used = out.size();
		assertTrue(used > MAX_PACKET_LENGTH);
		assertTrue(used <= MIN_CONNECTION_LENGTH);
	}
}
