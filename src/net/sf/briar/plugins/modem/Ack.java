package net.sf.briar.plugins.modem;

import net.sf.briar.util.ByteUtils;

class Ack extends Frame {

	static final int LENGTH = 12;

	Ack() {
		super(new byte[LENGTH], LENGTH);
		b[0] = (byte) Frame.ACK_FLAG;
	}

	Ack(byte[] b) {
		super(b, LENGTH);
		b[0] = (byte) Frame.ACK_FLAG;
	}

	int getWindowSize() {
		return ByteUtils.readUint24(b, 5);
	}

	void setWindowSize(int windowSize) {
		ByteUtils.writeUint24(windowSize, b, 5);
	}
}
