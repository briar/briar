package net.sf.briar.crypto;

class Sec1Utils {

	static void convertToFixedLength(byte[] src, byte[] dest, int destLen,
			int destOff) {
		if(src.length < destLen) {
			destOff += destLen - src.length;
			System.arraycopy(src, 0, dest, destOff, src.length);
		} else {
			int srcOff = src.length - destLen;
			System.arraycopy(src, srcOff, dest, destOff, destLen);
		}
	}
}
