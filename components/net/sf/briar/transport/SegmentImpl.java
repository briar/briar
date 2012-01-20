package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_SEGMENT_LENGTH;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;
import net.sf.briar.api.transport.Segment;

class SegmentImpl implements Segment {

	private final byte[] buf;

	private int length = -1;
	private long segmentNumber = -1;

	SegmentImpl() {
		this(MAX_SEGMENT_LENGTH);
	}

	SegmentImpl(int length) {
		if(length < FRAME_HEADER_LENGTH + MAC_LENGTH)
			throw new IllegalArgumentException();
		if(length > MAX_SEGMENT_LENGTH) throw new IllegalArgumentException();
		buf = new byte[length];
	}

	public byte[] getBuffer() {
		return buf;
	}

	public int getLength() {
		if(length == -1) throw new IllegalStateException();
		return length;
	}

	public long getSegmentNumber() {
		if(segmentNumber == -1) throw new IllegalStateException();
		return segmentNumber;
	}

	public void setLength(int length) {
		if(length < 0 || length > buf.length)
			throw new IllegalArgumentException();
		this.length = length;
	}

	public void setSegmentNumber(long segmentNumber) {
		if(segmentNumber < 0 || segmentNumber > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		this.segmentNumber = segmentNumber;
	}
}
