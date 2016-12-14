package org.briarproject.bramble.transport;

import org.briarproject.bramble.api.crypto.StreamDecrypter;
import org.briarproject.bramble.api.crypto.StreamEncrypter;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.briarproject.bramble.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.MAC_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.STREAM_HEADER_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.TAG_LENGTH;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class StreamReaderWriterIntegrationTest extends BrambleTestCase {

	@Test
	public void testWriteAndRead() throws Exception {
		// Generate a random tag
		byte[] tag = TestUtils.getRandomBytes(TAG_LENGTH);
		// Generate two frames with random payloads
		byte[] payload1 = TestUtils.getRandomBytes(123);
		byte[] payload2 = TestUtils.getRandomBytes(321);
		// Write the tag and the frames
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypter encrypter = new TestStreamEncrypter(out, tag);
		OutputStream streamWriter = new StreamWriterImpl(encrypter);
		streamWriter.write(payload1);
		streamWriter.flush();
		streamWriter.write(payload2);
		streamWriter.flush();
		byte[] output = out.toByteArray();
		assertEquals(TAG_LENGTH + STREAM_HEADER_LENGTH
				+ FRAME_HEADER_LENGTH  + payload1.length + MAC_LENGTH
				+ FRAME_HEADER_LENGTH  + payload2.length + MAC_LENGTH,
				output.length);
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
