package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import net.sf.briar.api.plugins.Segment;
import net.sf.briar.util.ByteUtils;

class SegmentImpl implements Segment {

	private final byte[] buf = new byte[MAX_FRAME_LENGTH];

	private int length = -1;
	private long transmission = -1;

	public void clear() {
		for(int i = 0; i < buf.length; i++) buf[i] = 0;
		length = -1;
		transmission = -1;
	}

	public byte[] getBuffer() {
		return buf;
	}

	public int getLength() {
		if(length == -1) throw new IllegalStateException();
		return length;
	}

	public long getTransmissionNumber() {
		if(transmission == -1) throw new IllegalStateException();
		return transmission;
	}

	public void setLength(int length) {
		if(length < 0 || length > buf.length)
			throw new IllegalArgumentException();
		this.length = length;
	}

	public void setTransmissionNumber(int transmission) {
		if(transmission < 0 || transmission > ByteUtils.MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		this.transmission = transmission;
	}
}
