package net.sf.briar.api.crypto;

public interface IvEncoder {

	byte[] encodeIv(long frameNumber);

	void updateIv(byte[] iv, long frameNumber);
}
