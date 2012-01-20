package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

class Frame {

	private final byte[] buf;

	private int length = -1;

	Frame() {
		this(MAX_FRAME_LENGTH);
	}

	Frame(int length) {
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
