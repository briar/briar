package org.briarproject.transport;

import static org.briarproject.api.transport.TransportConstants.MAX_PAYLOAD_LENGTH;

import java.io.IOException;
import java.io.OutputStream;

import org.briarproject.api.crypto.StreamEncrypter;

/**
 * A {@link org.briarproject.api.transport.StreamWriter StreamWriter} that
 * buffers its input and writes a frame whenever there is a full frame to write
 * or the {@link #flush()} method is called.
 * <p>
 * This class is not thread-safe.
 */
class StreamWriterImpl extends OutputStream {

	private final StreamEncrypter encrypter;
	private final byte[] payload;

	private int length = 0;

	StreamWriterImpl(StreamEncrypter encrypter) {
		this.encrypter = encrypter;
		payload = new byte[MAX_PAYLOAD_LENGTH];
	}

	@Override
	public void close() throws IOException {
		writeFrame(true);
		encrypter.flush();
		super.close();
	}

	@Override
	public void flush() throws IOException {
		writeFrame(false);
		encrypter.flush();
	}

	@Override
	public void write(int b) throws IOException {
		payload[length] = (byte) b;
		length++;
		if(length == payload.length) writeFrame(false);
	}

	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		int available = payload.length - length;
		while(available <= len) {
			System.arraycopy(b, off, payload, length, available);
			length += available;
			writeFrame(false);
			off += available;
			len -= available;
			available = payload.length - length;
		}
		System.arraycopy(b, off, payload, length, len);
		length += len;
	}

	private void writeFrame(boolean finalFrame) throws IOException {
		encrypter.writeFrame(payload, length, 0, finalFrame);
		length = 0;
	}
}
