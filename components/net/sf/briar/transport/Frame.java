package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

class Frame {

	private final byte[] buf = new byte[MAX_FRAME_LENGTH];

	private int length = -1;

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
