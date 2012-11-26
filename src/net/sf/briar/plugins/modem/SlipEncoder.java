package net.sf.briar.plugins.modem;

import java.io.IOException;

class SlipEncoder implements WriteHandler {

	// https://tools.ietf.org/html/rfc1055
	private static final byte END = (byte) 192, ESC = (byte) 219;
	private static final byte TEND = (byte) 220, TESC = (byte) 221;

	private final WriteHandler writeHandler;

	SlipEncoder(WriteHandler writeHandler) {
		this.writeHandler = writeHandler;
	}

	public void handleWrite(byte[] b, int length) throws IOException {
		if(length > Data.MAX_LENGTH) throw new IllegalArgumentException();
		int encodedLength = length + 2;
		for(int i = 0; i < length; i++) {
			if(b[i] == END || b[i] == ESC) encodedLength++;
		}
		byte[] buf = new byte[encodedLength];
		buf[0] = END;
		for(int i = 0, j = 1; i < length; i++) {
			if(b[i] == END) {
				buf[j++] = ESC;
				buf[j++] = TEND;
			} else if(b[i] == ESC) {
				buf[j++] = ESC;
				buf[j++] = TESC;
			} else {
				buf[j++] = b[i];
			}
		}
		buf[encodedLength - 1] = END;
		writeHandler.handleWrite(buf, encodedLength);
	}
}
