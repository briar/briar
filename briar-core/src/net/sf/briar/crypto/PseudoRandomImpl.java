package net.sf.briar.crypto;

import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.util.ByteUtils;

class PseudoRandomImpl implements PseudoRandom {

	private final MessageDigest messageDigest;

	private byte[] state;
	private int offset;

	PseudoRandomImpl(MessageDigest messageDigest, int seed1, int seed2) {
		this.messageDigest = messageDigest;
		byte[] seedBytes = new byte[8];
		ByteUtils.writeUint32(seed1, seedBytes, 0);
		ByteUtils.writeUint32(seed2, seedBytes, 4);
		messageDigest.update(seedBytes);
		state = messageDigest.digest();
		offset = 0;
	}

	public synchronized byte[] nextBytes(int bytes) {
		byte[] b = new byte[bytes];
		int half = state.length / 2;
		int off = 0, len = b.length, available = half - offset;
		while(available < len) {
			System.arraycopy(state, offset, b, off, available);
			off += available;
			len -= available;
			messageDigest.update(state, half, half);
			state = messageDigest.digest();
			offset = 0;
			available = half;
		}
		System.arraycopy(state, offset, b, off, len);
		offset += len;
		return b;
	}
}
