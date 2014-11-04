package org.briarproject.transport;

import static org.briarproject.api.transport.TransportConstants.HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAC_LENGTH;

import java.io.IOException;
import java.io.OutputStream;

import org.briarproject.api.transport.StreamWriter;

/**
 * A {@link org.briarproject.api.transport.StreamWriter StreamWriter} that
 * buffers its input and writes a frame whenever there is a full frame to write
 * or the {@link #flush()} method is called.
 * <p>
 * This class is not thread-safe.
 */
class StreamWriterImpl extends OutputStream implements StreamWriter {

	private final FrameWriter out;
	private final byte[] frame;
	private final int frameLength;

	private int length = 0;

	StreamWriterImpl(FrameWriter out, int frameLength) {
		this.out = out;
		this.frameLength = frameLength;
		frame = new byte[frameLength - MAC_LENGTH];
	}

	public OutputStream getOutputStream() {
		return this;
	}

	@Override
	public void close() throws IOException {
		writeFrame(true);
		out.flush();
		super.close();
	}

	@Override
	public void flush() throws IOException {
		if(length > 0) writeFrame(false);
		out.flush();
	}

	@Override
	public void write(int b) throws IOException {
		frame[HEADER_LENGTH + length] = (byte) b;
		length++;
		if(HEADER_LENGTH + length + MAC_LENGTH == frameLength)
			writeFrame(false);
	}

	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		int available = frameLength - HEADER_LENGTH - length - MAC_LENGTH;
		while(available <= len) {
			System.arraycopy(b, off, frame, HEADER_LENGTH + length, available);
			length += available;
			writeFrame(false);
			off += available;
			len -= available;
			available = frameLength - HEADER_LENGTH - length - MAC_LENGTH;
		}
		System.arraycopy(b, off, frame, HEADER_LENGTH + length, len);
		length += len;
	}

	private void writeFrame(boolean finalFrame) throws IOException {
		out.writeFrame(frame, length, finalFrame);
		length = 0;
	}
}
