package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.io.IOException;
import java.io.OutputStream;

import net.sf.briar.api.transport.ConnectionWriter;

/**
 * A ConnectionWriter that buffers its input and writes a frame whenever there
 * is a full-size frame to write or the flush() method is called.
 * <p>
 * This class is not thread-safe.
 */
class ConnectionWriterImpl extends OutputStream implements ConnectionWriter {

	private final FrameWriter out;
	private final byte[] frame;

	private int offset;
	private long frameNumber;

	ConnectionWriterImpl(FrameWriter out) {
		this.out = out;
		frame = new byte[MAX_FRAME_LENGTH];
		offset = HEADER_LENGTH;
		frameNumber = 0L;
	}

	public OutputStream getOutputStream() {
		return this;
	}

	public long getRemainingCapacity() {
		long capacity = out.getRemainingCapacity();
		// If there's any data buffered, subtract it and its overhead
		if(offset > HEADER_LENGTH) capacity -= offset + MAC_LENGTH;
		// Subtract the overhead from the remaining capacity
		long frames = (long) Math.ceil((double) capacity / MAX_FRAME_LENGTH);
		int overheadPerFrame = HEADER_LENGTH + MAC_LENGTH;
		return Math.max(0L, capacity - frames * overheadPerFrame);
	}

	@Override
	public void close() throws IOException {
		if(offset > HEADER_LENGTH || frameNumber > 0L) writeFrame(true);
		out.flush();
		super.close();
	}

	@Override
	public void flush() throws IOException {
		if(offset > HEADER_LENGTH) writeFrame(false);
		out.flush();
	}

	@Override
	public void write(int b) throws IOException {
		frame[offset++] = (byte) b;
		if(offset + MAC_LENGTH == MAX_FRAME_LENGTH) writeFrame(false);
	}

	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		int available = MAX_FRAME_LENGTH - offset - MAC_LENGTH;
		while(available <= len) {
			System.arraycopy(b, off, frame, offset, available);
			offset += available;
			writeFrame(false);
			off += available;
			len -= available;
			available = MAX_FRAME_LENGTH - offset - MAC_LENGTH;
		}
		System.arraycopy(b, off, frame, offset, len);
		offset += len;
	}

	private void writeFrame(boolean lastFrame) throws IOException {
		if(frameNumber > MAX_32_BIT_UNSIGNED) throw new IllegalStateException();
		int payload = offset - HEADER_LENGTH;
		assert payload >= 0;
		HeaderEncoder.encodeHeader(frame, frameNumber, payload, 0, lastFrame);
		out.writeFrame(frame);
		offset = HEADER_LENGTH;
		frameNumber++;
	}
}
