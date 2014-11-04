package org.briarproject.transport;

import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;
import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

import org.briarproject.BriarTestCase;
import org.briarproject.TestLifecycleModule;
import org.briarproject.TestSystemModule;
import org.briarproject.api.crypto.AuthenticatedCipher;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.SecretKey;
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
	private final AuthenticatedCipher frameCipher;
	private final Random random;
	private final byte[] secret;
	private final SecretKey tagKey, frameKey;

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
		frameCipher = crypto.getFrameCipher();
		random = new Random();
		// Since we're sending frames to ourselves, we only need outgoing keys
		secret = new byte[32];
		random.nextBytes(secret);
		tagKey = crypto.deriveTagKey(secret, true);
		frameKey = crypto.deriveFrameKey(secret, 0, true);
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
		// Encode the tag
		byte[] tag = new byte[TAG_LENGTH];
		crypto.encodeTag(tag, tagKey, 0);
		// Generate two random frames
		byte[] frame = new byte[1234];
		random.nextBytes(frame);
		byte[] frame1 = new byte[321];
		random.nextBytes(frame1);
		// Copy the frame key - the copy will be erased
		SecretKey frameCopy = frameKey.copy();
		// Write the tag and the frames
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		FrameWriter frameWriter = new OutgoingEncryptionLayer(out,
				frameCipher, frameCopy, FRAME_LENGTH, tag);
		StreamWriterImpl streamWriter = new StreamWriterImpl(frameWriter,
				FRAME_LENGTH);
		OutputStream out1 = streamWriter.getOutputStream();
		out1.write(frame);
		out1.flush();
		out1.write(frame1);
		out1.flush();
		byte[] output = out.toByteArray();
		assertEquals(TAG_LENGTH + FRAME_LENGTH * 2, output.length);
		// Read the tag back
		ByteArrayInputStream in = new ByteArrayInputStream(output);
		byte[] recoveredTag = new byte[tag.length];
		read(in, recoveredTag);
		assertArrayEquals(tag, recoveredTag);
		// Read the frames back
		FrameReader frameReader = new IncomingEncryptionLayer(in, frameCipher,
				frameKey, FRAME_LENGTH);
		StreamReaderImpl streamReader = new StreamReaderImpl(frameReader,
				FRAME_LENGTH);
		InputStream in1 = streamReader.getInputStream();
		byte[] recoveredFrame = new byte[frame.length];
		read(in1, recoveredFrame);
		assertArrayEquals(frame, recoveredFrame);
		byte[] recoveredFrame1 = new byte[frame1.length];
		read(in1, recoveredFrame1);
		assertArrayEquals(frame1, recoveredFrame1);
		streamWriter.close();
		streamReader.close();
	}

	private void read(InputStream in, byte[] dest) throws IOException {
		int offset = 0;
		while(offset < dest.length) {
			int read = in.read(dest, offset, dest.length - offset);
			if(read == -1) break;
			offset += read;
		}
	}
}
