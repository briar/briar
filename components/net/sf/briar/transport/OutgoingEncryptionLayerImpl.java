package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_SEGMENT_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.transport.Segment;

class OutgoingEncryptionLayerImpl implements OutgoingEncryptionLayer {

	private final OutputStream out;
	private final Cipher tagCipher, segCipher;
	private final ErasableKey tagKey, segKey;
	private final boolean tagEverySegment;
	private final byte[] iv, ciphertext;

	private long capacity;

	OutgoingEncryptionLayerImpl(OutputStream out, long capacity,
			Cipher tagCipher, Cipher segCipher, ErasableKey tagKey,
			ErasableKey segKey, boolean tagEverySegment) {
		this.out = out;
		this.capacity = capacity;
		this.tagCipher = tagCipher;
		this.segCipher = segCipher;
		this.tagKey = tagKey;
		this.segKey = segKey;
		this.tagEverySegment = tagEverySegment;
		iv = IvEncoder.encodeIv(0L, segCipher.getBlockSize());
		ciphertext = new byte[MAX_SEGMENT_LENGTH];
	}

	public void writeSegment(Segment s) throws IOException {
		byte[] plaintext = s.getBuffer();
		int length = s.getLength();
		long segmentNumber = s.getSegmentNumber();
		int offset = 0;
		if(tagEverySegment || segmentNumber == 0) {
			TagEncoder.encodeTag(ciphertext, segmentNumber, tagCipher, tagKey);
			offset = TAG_LENGTH;
		}
		IvEncoder.updateIv(iv, segmentNumber);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		try {
			segCipher.init(Cipher.ENCRYPT_MODE, segKey, ivSpec);
			int encrypted = segCipher.doFinal(plaintext, 0, length,
					ciphertext, offset);
			if(encrypted != length) throw new RuntimeException();
		} catch(GeneralSecurityException badCipher) {
			throw new RuntimeException(badCipher);
		}
		try {
			out.write(ciphertext, 0, offset + length);
		} catch(IOException e) {
			segKey.erase();
			tagKey.erase();
			throw e;
		}
		capacity -= offset + length;
	}

	public void flush() throws IOException {
		out.flush();
	}

	public long getRemainingCapacity() {
		return capacity;
	}
}