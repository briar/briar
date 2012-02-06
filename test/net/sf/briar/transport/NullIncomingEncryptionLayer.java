package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import net.sf.briar.api.FormatException;

/** An encryption layer that performs no encryption. */
class NullIncomingEncryptionLayer implements FrameReader {

	private final InputStream in;

	NullIncomingEncryptionLayer(InputStream in) {
		this.in = in;
	}

	public boolean readFrame(Frame f) throws IOException {
		byte[] buf = f.getBuffer();
		// Read the frame header
		int offset = 0, length = FRAME_HEADER_LENGTH;
		while(offset < length) {
			int read = in.read(buf, offset, length - offset);
			if(read == -1) {
				if(offset == 0) return false;
				throw new EOFException();
			}
			offset += read;
		}
		// Parse the frame header
		int payload = HeaderEncoder.getPayloadLength(buf);
		int padding = HeaderEncoder.getPaddingLength(buf);
		length = FRAME_HEADER_LENGTH + payload + padding + MAC_LENGTH;
		if(length > MAX_FRAME_LENGTH) throw new FormatException();
		// Read the remainder of the frame
		while(offset < length) {
			int read = in.read(buf, offset, length - offset);
			if(read == -1) throw new EOFException();
			offset += read;
		}
		f.setLength(length);
		return true;
	}
}
