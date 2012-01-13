package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import net.sf.briar.api.FormatException;

/** A connection decrypter that performs no decryption. */
class NullConnectionDecrypter implements FrameSource {

	private final InputStream in;
	private final int macLength;

	NullConnectionDecrypter(InputStream in, int macLength) {
		this.in = in;
		this.macLength = macLength;
	}

	public int readFrame(byte[] b) throws IOException {
		if(b.length < MAX_FRAME_LENGTH) throw new IllegalArgumentException();
		// Read the header to determine the frame length
		int offset = 0, length = FRAME_HEADER_LENGTH;
		while(offset < length) {
			int read = in.read(b, offset, length - offset);
			if(read == -1) {
				if(offset == 0) return -1;
				throw new EOFException();
			}
			offset += read;
		}
		// Parse the header
		int payload = HeaderEncoder.getPayloadLength(b);
		int padding = HeaderEncoder.getPaddingLength(b);
		length = FRAME_HEADER_LENGTH + payload + padding + macLength;
		if(length > MAX_FRAME_LENGTH) throw new FormatException();
		// Read the remainder of the frame
		while(offset < length) {
			int read = in.read(b, offset, length - offset);
			if(read == -1) throw new EOFException();
			offset += read;
		}
		return length;
	}
}
