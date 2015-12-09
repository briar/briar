package org.briarproject.transport;

import org.briarproject.BriarTestCase;
import org.briarproject.api.crypto.StreamDecrypter;
import org.briarproject.api.crypto.StreamEncrypter;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

import static org.briarproject.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class TransportIntegrationTest extends BriarTestCase {

	private final Random random;

	public TransportIntegrationTest() {
		random = new Random();
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
		// Generate a random tag
		byte[] tag = new byte[TAG_LENGTH];
		random.nextBytes(tag);
		// Generate two frames with random payloads
		byte[] payload1 = new byte[123];
		random.nextBytes(payload1);
		byte[] payload2 = new byte[321];
		random.nextBytes(payload2);
		// Write the tag and the frames
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypter encrypter = new TestStreamEncrypter(out, tag);
		OutputStream streamWriter = new StreamWriterImpl(encrypter);
		streamWriter.write(payload1);
		streamWriter.flush();
		streamWriter.write(payload2);
		streamWriter.flush();
		byte[] output = out.toByteArray();
		assertEquals(TAG_LENGTH + MAX_FRAME_LENGTH * 2, output.length);
		// Read the tag back
		ByteArrayInputStream in = new ByteArrayInputStream(output);
		byte[] recoveredTag = new byte[tag.length];
		read(in, recoveredTag);
		assertArrayEquals(tag, recoveredTag);
		// Read the frames back
		StreamDecrypter decrypter = new TestStreamDecrypter(in);
		InputStream streamReader = new StreamReaderImpl(decrypter);
		byte[] recoveredPayload1 = new byte[payload1.length];
		read(streamReader, recoveredPayload1);
		assertArrayEquals(payload1, recoveredPayload1);
		byte[] recoveredPayload2 = new byte[payload2.length];
		read(streamReader, recoveredPayload2);
		assertArrayEquals(payload2, recoveredPayload2);
		streamWriter.close();
		streamReader.close();
	}

	private void read(InputStream in, byte[] dest) throws IOException {
		int offset = 0;
		while (offset < dest.length) {
			int read = in.read(dest, offset, dest.length - offset);
			if (read == -1) break;
			offset += read;
		}
	}
}
