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

	public void handleWrite(byte[] b) throws IOException {
		if(b.length > Data.MAX_LENGTH) throw new IllegalArgumentException();
		int encodedLength = b.length + 2;
		for(int i = 0; i < b.length; i++)
			if(b[i] == END || b[i] == ESC) encodedLength++;
		byte[] encoded = new byte[encodedLength];
		encoded[0] = END;
		for(int i = 0, j = 1; i < b.length; i++) {
			if(b[i] == END) {
				encoded[j++] = ESC;
				encoded[j++] = TEND;
			} else if(b[i] == ESC) {
				encoded[j++] = ESC;
				encoded[j++] = TESC;
			} else {
				encoded[j++] = b[i];
			}
		}
		encoded[encodedLength - 1] = END;
		writeHandler.handleWrite(encoded);
	}
}
