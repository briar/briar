package org.briarproject.transport;

import static org.briarproject.api.messaging.MessagingConstants.MAX_PACKET_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MIN_STREAM_LENGTH;
import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

import org.briarproject.BriarTestCase;
import org.briarproject.TestLifecycleModule;
import org.briarproject.TestSystemModule;
import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.crypto.AuthenticatedCipher;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.StreamWriter;
import org.briarproject.api.transport.StreamWriterFactory;
import org.briarproject.crypto.CryptoModule;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

public class TransportIntegrationTest extends BriarTestCase {

	private final int FRAME_LENGTH = 2048;

	private final CryptoComponent crypto;
	private final StreamWriterFactory streamWriterFactory;
	private final ContactId contactId;
	private final TransportId transportId;
	private final AuthenticatedCipher frameCipher;
	private final Random random;
	private final byte[] secret;
	private final SecretKey frameKey;

	public TransportIntegrationTest() {
		Module testModule = new AbstractModule() {
			@Override
			public void configure() {
				bind(StreamWriterFactory.class).to(
						StreamWriterFactoryImpl.class);
			}
		};
		Injector i = Guice.createInjector(testModule, new CryptoModule(),
				new TestLifecycleModule(), new TestSystemModule());
		crypto = i.getInstance(CryptoComponent.class);
		streamWriterFactory = i.getInstance(StreamWriterFactory.class);
		contactId = new ContactId(234);
		transportId = new TransportId("id");
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
		SecretKey frameCopy = frameKey.copy();
		// Write the frames
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		FrameWriter encryptionOut = new OutgoingEncryptionLayer(out,
				Long.MAX_VALUE, frameCipher, frameCopy, FRAME_LENGTH);
		StreamWriterImpl writer = new StreamWriterImpl(encryptionOut,
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
		StreamReaderImpl reader = new StreamReaderImpl(encryptionIn,
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
		writer.close();
		reader.close();
	}

	@Test
	public void testOverheadWithTag() throws Exception {
		ByteArrayOutputStream out =
				new ByteArrayOutputStream(MIN_STREAM_LENGTH);
		StreamContext ctx = new StreamContext(contactId, transportId,
				secret, 0, true);
		StreamWriter w = streamWriterFactory.createStreamWriter(out,
				MAX_FRAME_LENGTH, MIN_STREAM_LENGTH, ctx, false, true);
		// Check that the connection writer thinks there's room for a packet
		long capacity = w.getRemainingCapacity();
		assertTrue(capacity > MAX_PACKET_LENGTH);
		assertTrue(capacity < MIN_STREAM_LENGTH);
		// Check that there really is room for a packet
		byte[] payload = new byte[MAX_PACKET_LENGTH];
		w.getOutputStream().write(payload);
		w.getOutputStream().close();
		long used = out.size();
		assertTrue(used > MAX_PACKET_LENGTH);
		assertTrue(used <= MIN_STREAM_LENGTH);
	}

	@Test
	public void testOverheadWithoutTag() throws Exception {
		ByteArrayOutputStream out =
				new ByteArrayOutputStream(MIN_STREAM_LENGTH);
		StreamContext ctx = new StreamContext(contactId, transportId,
				secret, 0, true);
		StreamWriter w = streamWriterFactory.createStreamWriter(out,
				MAX_FRAME_LENGTH, MIN_STREAM_LENGTH, ctx, false, false);
		// Check that the connection writer thinks there's room for a packet
		long capacity = w.getRemainingCapacity();
		assertTrue(capacity > MAX_PACKET_LENGTH);
		assertTrue(capacity < MIN_STREAM_LENGTH);
		// Check that there really is room for a packet
		byte[] payload = new byte[MAX_PACKET_LENGTH];
		w.getOutputStream().write(payload);
		w.getOutputStream().close();
		long used = out.size();
		assertTrue(used > MAX_PACKET_LENGTH);
		assertTrue(used <= MIN_STREAM_LENGTH);
	}
}
