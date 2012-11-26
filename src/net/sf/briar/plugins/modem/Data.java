package net.sf.briar.plugins.modem;

class Data extends Frame {

	static final int HEADER_LENGTH = 5, FOOTER_LENGTH = 4;
	static final int MIN_LENGTH = HEADER_LENGTH + FOOTER_LENGTH;
	static final int MAX_PAYLOAD_LENGTH = 1024;
	static final int MAX_LENGTH = MIN_LENGTH + MAX_PAYLOAD_LENGTH;

	Data(byte[] b, int length) {
		super(b, length);
		if(length < MIN_LENGTH || length > MAX_LENGTH)
			throw new IllegalArgumentException();
	}

	boolean isLastFrame() {
		return b[0] == Frame.FIN_FLAG;
	}

	void setLastFrame(boolean lastFrame) {
		if(lastFrame) b[0] = (byte) Frame.FIN_FLAG;
	}

	int getPayloadLength() {
		return length - MIN_LENGTH;
	}
}
