package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.ACK_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
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

	private final OutgoingReliabilityLayer out;
	private final int headerLength, maxFrameLength;
	private final Frame frame;

	private int offset;
	private long frameNumber;

	ConnectionWriterImpl(OutgoingReliabilityLayer out, boolean ackHeader) {
		this.out = out;
		if(ackHeader) headerLength = FRAME_HEADER_LENGTH + ACK_HEADER_LENGTH;
		else headerLength = FRAME_HEADER_LENGTH;
		maxFrameLength = out.getMaxFrameLength();
		frame = new Frame(maxFrameLength);
		offset = headerLength;
		frameNumber = 0L;
	}

	public OutputStream getOutputStream() {
		return this;
	}

	public long getRemainingCapacity() {
		long capacity = out.getRemainingCapacity();
		// If there's any data buffered, subtract it and its overhead
		if(offset > headerLength) capacity -= offset + MAC_LENGTH;
		// Subtract the overhead from the remaining capacity
		long frames = (long) Math.ceil((double) capacity / maxFrameLength);
		int overheadPerFrame = headerLength + MAC_LENGTH;
		return Math.max(0L, capacity - frames * overheadPerFrame);
	}

	@Override
	public void flush() throws IOException {
		if(offset > headerLength) writeFrame();
		out.flush();
	}

	@Override
	public void write(int b) throws IOException {
		frame.getBuffer()[offset++] = (byte) b;
		if(offset + MAC_LENGTH == maxFrameLength) writeFrame();
	}

	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		byte[] buf = frame.getBuffer();
		int available = maxFrameLength - offset - MAC_LENGTH;
		while(available <= len) {
			System.arraycopy(b, off, buf, offset, available);
			offset += available;
			writeFrame();
			off += available;
			len -= available;
			available = maxFrameLength - offset - MAC_LENGTH;
		}
		System.arraycopy(b, off, buf, offset, len);
		offset += len;
	}

	private void writeFrame() throws IOException {
		if(frameNumber > MAX_32_BIT_UNSIGNED) throw new IllegalStateException();
		int payload = offset - headerLength;
		assert payload > 0;
		HeaderEncoder.encodeHeader(frame.getBuffer(), frameNumber, payload, 0);
		frame.setLength(offset + MAC_LENGTH);
		out.writeFrame(frame);
		offset = headerLength;
		frameNumber++;
	}
}
