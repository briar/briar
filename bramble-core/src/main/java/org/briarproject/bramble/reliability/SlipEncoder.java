package org.briarproject.bramble.reliability;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.reliability.WriteHandler;

import java.io.IOException;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
class SlipEncoder implements WriteHandler {

	// https://tools.ietf.org/html/rfc1055
	private static final byte END = (byte) 192, ESC = (byte) 219;
	private static final byte TEND = (byte) 220, TESC = (byte) 221;

	private final WriteHandler writeHandler;

	SlipEncoder(WriteHandler writeHandler) {
		this.writeHandler = writeHandler;
	}

	@Override
	public void handleWrite(byte[] b) throws IOException {
		int encodedLength = b.length + 2;
		for (int i = 0; i < b.length; i++)
			if (b[i] == END || b[i] == ESC) encodedLength++;
		byte[] encoded = new byte[encodedLength];
		encoded[0] = END;
		for (int i = 0, j = 1; i < b.length; i++) {
			if (b[i] == END) {
				encoded[j++] = ESC;
				encoded[j++] = TEND;
			} else if (b[i] == ESC) {
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
