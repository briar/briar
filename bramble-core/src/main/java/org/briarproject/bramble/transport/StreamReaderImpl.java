package org.briarproject.bramble.transport;

import org.briarproject.bramble.api.crypto.StreamDecrypter;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.concurrent.NotThreadSafe;

import static org.briarproject.bramble.api.transport.TransportConstants.MAX_PAYLOAD_LENGTH;

/**
 * An {@link InputStream} that unpacks payload data from transport frames.
 */
@NotThreadSafe
@NotNullByDefault
class StreamReaderImpl extends InputStream {

	private final StreamDecrypter decrypter;
	private final byte[] payload;

	private int offset = 0, length = 0;

	StreamReaderImpl(StreamDecrypter decrypter) {
		this.decrypter = decrypter;
		payload = new byte[MAX_PAYLOAD_LENGTH];
	}

	@Override
	public int read() throws IOException {
		while (length <= 0) {
			if (length == -1) return -1;
			readFrame();
		}
		int b = payload[offset] & 0xff;
		offset++;
		length--;
		return b;
	}

	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		while (length <= 0) {
			if (length == -1) return -1;
			readFrame();
		}
		len = Math.min(len, length);
		System.arraycopy(payload, offset, b, off, len);
		offset += len;
		length -= len;
		return len;
	}

	private void readFrame() throws IOException {
		if (length != 0) throw new IllegalStateException();
		offset = 0;
		length = decrypter.readFrame(payload);
	}
}
