package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.io.IOException;
import java.io.OutputStream;

import net.sf.briar.api.transport.ConnectionWriter;

/**
 * A ConnectionWriter that buffers its input and writes a frame whenever there
 * is a full frame to write or the flush() method is called.
 * <p>
 * This class is not thread-safe.
 */
class ConnectionWriterImpl extends OutputStream implements ConnectionWriter {

	private final FrameWriter out;
	private final byte[] frame;
	private final int frameLength;

	private int length = 0;
	private long frameNumber = 0L;

	ConnectionWriterImpl(FrameWriter out, int frameLength) {
		this.out = out;
		this.frameLength = frameLength;
		frame = new byte[frameLength];
	}

	public OutputStream getOutputStream() {
		return this;
	}

	public long getRemainingCapacity() {
		return out.getRemainingCapacity();
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
		if(frameNumber > MAX_32_BIT_UNSIGNED) throw new IllegalStateException();
		out.writeFrame(frame, length, finalFrame);
		length = 0;
		frameNumber++;
	}
}
