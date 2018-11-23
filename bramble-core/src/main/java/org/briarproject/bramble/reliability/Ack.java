package org.briarproject.bramble.reliability;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.NotThreadSafe;

import static org.briarproject.bramble.util.ByteUtils.readUint16;
import static org.briarproject.bramble.util.ByteUtils.writeUint16;

@NotThreadSafe
@NotNullByDefault
class Ack extends Frame {

	static final int LENGTH = 11;

	Ack() {
		super(new byte[LENGTH]);
		buf[0] = Frame.ACK_FLAG;
	}

	Ack(byte[] buf) {
		super(buf);
		if (buf.length != LENGTH) throw new IllegalArgumentException();
		buf[0] = Frame.ACK_FLAG;
	}

	int getWindowSize() {
		return readUint16(buf, 5);
	}

	void setWindowSize(int windowSize) {
		writeUint16(windowSize, buf, 5);
	}
}
