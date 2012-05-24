package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

class Frame {

	private final byte[] buf;

	private int length = -1;

	Frame() {
		buf = new byte[MAX_FRAME_LENGTH];
	}

	public byte[] getBuffer() {
		return buf;
	}

	public int getLength() {
		if(length == -1) throw new IllegalStateException();
		return length;
	}

	public void setLength(int length) {
		if(length < FRAME_HEADER_LENGTH || length > buf.length)
			throw new IllegalArgumentException();
		this.length = length;
	}

	public void reset() {
		length = -1;
	}
}
