package org.briarproject.bramble.reliability;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.reliability.ReadHandler;

import java.io.IOException;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
class SlipDecoder implements ReadHandler {

	// https://tools.ietf.org/html/rfc1055
	private static final byte END = (byte) 192, ESC = (byte) 219;
	private static final byte TEND = (byte) 220, TESC = (byte) 221;

	private final ReadHandler readHandler;
	private final byte[] buf;

	private int decodedLength = 0;
	private boolean escape = false;

	SlipDecoder(ReadHandler readHandler, int maxDecodedLength) {
		this.readHandler = readHandler;
		buf = new byte[maxDecodedLength];
	}

	@Override
	public void handleRead(byte[] b) throws IOException {
		for (int i = 0; i < b.length; i++) {
			switch(b[i]) {
			case END:
				if (escape) {
					reset(true);
				} else {
					if (decodedLength > 0) {
						byte[] decoded = new byte[decodedLength];
						System.arraycopy(buf, 0, decoded, 0, decodedLength);
						readHandler.handleRead(decoded);
					}
					reset(false);
				}
				break;
			case ESC:
				if (escape) reset(true);
				else escape = true;
				break;
			case TEND:
				if (escape) {
					escape = false;
					if (decodedLength == buf.length) reset(true);
					else buf[decodedLength++] = END;
				} else {
					if (decodedLength == buf.length) reset(true);
					else buf[decodedLength++] = TEND;
				}
				break;
			case TESC:
				if (escape) {
					escape = false;
					if (decodedLength == buf.length) reset(true);
					else buf[decodedLength++] = ESC;
				} else {
					if (decodedLength == buf.length) reset(true);
					else buf[decodedLength++] = TESC;
				}
				break;
			default:
				if (escape || decodedLength == buf.length) reset(true);
				else buf[decodedLength++] = b[i];
				break;
			}
		}
	}

	private void reset(boolean error) {
		escape = false;
		decodedLength = 0;
	}
}
