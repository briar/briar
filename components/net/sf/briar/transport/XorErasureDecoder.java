package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.ACK_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.transport.Segment;

/** An erasure decoder that uses k data segments and one parity segment. */
class XorErasureDecoder implements ErasureDecoder {

	private final int n, headerLength;

	XorErasureDecoder(int n, boolean ackHeader) {
		this.n = n;
		if(ackHeader) headerLength = FRAME_HEADER_LENGTH + ACK_HEADER_LENGTH;
		else headerLength = FRAME_HEADER_LENGTH;
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
				int copyLength = Math.min(length, dest.length - offset);
				System.arraycopy(src, 0, dest, offset, copyLength);
				offset += length;
			}
		} else {
			// Reconstruct the missing segment
			byte[] parity = new byte[length];
			int missingOffset = -1;
			for(int i = 0; i < n - 1; i++) {
				if(set[i] == null) {
					missingOffset = offset;
				} else {
					byte[] src = set[i].getBuffer();
					for(int j = 0; j < length; j++) parity[j] ^= src[j];
					int copyLength = Math.min(length, dest.length - offset);
					System.arraycopy(src, 0, dest, offset, copyLength);
				}
				offset += length;
			}
			byte[] src = set[n - 1].getBuffer();
			for(int i = 0; i < length; i++) parity[i] ^= src[i];
			assert missingOffset != -1;
			int copyLength = Math.min(length, dest.length - missingOffset);
			System.arraycopy(parity, 0, dest, missingOffset, copyLength);
		}
		// The frame length might not be an exact multiple of the segment length
		int payload = HeaderEncoder.getPayloadLength(dest);
		int padding = HeaderEncoder.getPaddingLength(dest);
		int frameLength = headerLength + payload + padding + MAC_LENGTH;
		if(frameLength > MAX_FRAME_LENGTH) throw new FormatException();
		f.setLength(frameLength);
		return true;
	}
}
