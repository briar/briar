package net.sf.briar.transport;

import net.sf.briar.api.transport.Segment;

/** An erasure encoder than uses k data segments and one parity segment. */
class XorErasureEncoder implements ErasureEncoder {

	private final int n;

	XorErasureEncoder(int n) {
		this.n = n;
	}

	public Segment[] encodeFrame(Frame f) {
		Segment[] set = new Segment[n];
		int length = (int) Math.ceil((float) f.getLength() / (n - 1));
		for(int i = 0; i < n; i++) {
			set[i] = new SegmentImpl(length);
			set[i].setLength(length);
		}
		byte[] src = f.getBuffer(), parity = set[n - 1].getBuffer();
		int offset = 0;
		for(int i = 0; i < n - 1; i++) {
			int copyLength = Math.min(length, src.length - offset);
			System.arraycopy(src, offset, set[i].getBuffer(), 0, copyLength);
			for(int j = 0; j < copyLength; j++) parity[j] ^= src[offset + j];
			offset += length;
		}
		return set;
	}
}
