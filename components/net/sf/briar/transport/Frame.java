package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_SEGMENT_LENGTH;

class Frame {

	private final byte[] buf;

	private int length = -1;

	Frame() {
		this(MAX_FRAME_LENGTH);
	}

	Frame(int length) {
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

	public void setLength(int length) {
		if(length < 0 || length > buf.length)
			throw new IllegalArgumentException();
		this.length = length;
	}
}
