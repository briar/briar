package net.sf.briar.transport;

import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.io.IOException;

import net.sf.briar.api.transport.Segment;

class NullOutgoingErrorCorrectionLayer implements OutgoingErrorCorrectionLayer {

	private final OutgoingEncryptionLayer out;
	private final Segment segment;

	private long segmentNumber = 0L;

	public NullOutgoingErrorCorrectionLayer(OutgoingEncryptionLayer out) {
		this.out = out;
		segment = new SegmentImpl();
	}

	public void writeFrame(Frame f) throws IOException {
		if(segmentNumber > MAX_32_BIT_UNSIGNED)
			throw new IllegalStateException();
		int length = f.getLength();
		// FIXME: Unnecessary copy
		System.arraycopy(f.getBuffer(), 0, segment.getBuffer(), 0, length);
		segment.setLength(length);
		segment.setSegmentNumber(segmentNumber++);
		out.writeSegment(segment);
	}

	public void flush() throws IOException {
		out.flush();
	}

	public long getRemainingCapacity() {
		return out.getRemainingCapacity();
	}
}
