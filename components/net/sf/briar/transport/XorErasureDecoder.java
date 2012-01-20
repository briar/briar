package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.transport.Segment;

/** An erasure decoder that uses k data segments and one parity segment. */
class XorErasureDecoder implements ErasureDecoder {

	private final int n;

	XorErasureDecoder(int n) {
		this.n = n;
	}

	public boolean decodeFrame(Frame f, Segment[] set) throws FormatException {
		// We need at least n - 1 pieces
		int pieces = 0;
		for(int i = 0; i < n; i++) if(set[i] != null) pieces++;
		if(pieces < n - 1) return false;
		// All the pieces must have the same length - take the minimum
		int length = MAX_FRAME_LENGTH;
		for(int i = 0; i < n; i++) {
			if(set[i] != null) {
				int len = set[i].getLength();
				if(len < length) length = len;
			}
		}
		if(length * (n - 1) > MAX_FRAME_LENGTH) throw new FormatException();
		// Decode the frame
		byte[] dest = f.getBuffer();
		int offset = 0;
		if(pieces == n || set[n - 1] == null) {
			// We don't need no stinkin' parity segment
			for(int i = 0; i < n - 1; i++) {
				byte[] src = set[i].getBuffer();
				System.arraycopy(src, 0, dest, offset, length);
				offset += length;
			}
		} else {
			// Reconstruct the missing segment
			byte[] parity = new byte[length];
			int missingOffset = -1;
			for(int i = 0; i < n; i++) {
				if(set[i] == null) {
					missingOffset = offset;
				} else {
					byte[] src = set[i].getBuffer();
					System.arraycopy(src, 0, dest, offset, length);
					for(int j = 0; j < length; j++) parity[j] ^= src[j];
				}
				offset += length;
			}
			assert missingOffset != -1;
			System.arraycopy(parity, 0, dest, missingOffset, length);
		}
		assert offset == length * (n - 1);
		// The frame length may not be an exact multiple of the segment length
		int payload = HeaderEncoder.getPayloadLength(dest);
		int padding = HeaderEncoder.getPaddingLength(dest);
		int frameLength = FRAME_HEADER_LENGTH + payload + padding + MAC_LENGTH;
		if(frameLength > MAX_FRAME_LENGTH) throw new FormatException();
		f.setLength(frameLength);
		return true;
	}
}
