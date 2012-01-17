package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_SEGMENT_LENGTH;
import net.sf.briar.api.plugins.Segment;
import net.sf.briar.util.ByteUtils;

class SegmentImpl implements Segment {

	private final byte[] buf = new byte[MAX_SEGMENT_LENGTH];

	private int length = -1;
	private long segmentNumber = -1;

	public void clear() {
		for(int i = 0; i < buf.length; i++) buf[i] = 0;
		length = -1;
		segmentNumber = -1;
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
		if(segmentNumber < 0 || segmentNumber > ByteUtils.MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		this.segmentNumber = segmentNumber;
	}
}
