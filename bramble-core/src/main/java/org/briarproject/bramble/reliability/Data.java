package org.briarproject.bramble.reliability;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class Data extends Frame {

	static final int HEADER_LENGTH = 5, FOOTER_LENGTH = 4;
	static final int MIN_LENGTH = HEADER_LENGTH + FOOTER_LENGTH;
	static final int MAX_PAYLOAD_LENGTH = 1024;
	static final int MAX_LENGTH = MIN_LENGTH + MAX_PAYLOAD_LENGTH;

	Data(byte[] buf) {
		super(buf);
		if (buf.length < MIN_LENGTH || buf.length > MAX_LENGTH)
			throw new IllegalArgumentException();
	}

	boolean isLastFrame() {
		return buf[0] == Frame.FIN_FLAG;
	}

	void setLastFrame(boolean lastFrame) {
		if (lastFrame) buf[0] = Frame.FIN_FLAG;
	}

	int getPayloadLength() {
		return buf.length - MIN_LENGTH;
	}
}
