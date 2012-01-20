package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_SEGMENT_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.transport.Segment;

class IncomingEncryptionLayerImpl implements IncomingEncryptionLayer {

	private final InputStream in;
	private final Cipher tagCipher, segCipher;
	private final ErasableKey tagKey, segKey;
	private final boolean tagEverySegment;
	private final int blockSize;
	private final byte[] iv, ciphertext;

	private byte[] bufferedTag;
	private boolean firstSegment = true;
	private long segmentNumber = 0L;

	IncomingEncryptionLayerImpl(InputStream in, Cipher tagCipher,
			Cipher segCipher, ErasableKey tagKey, ErasableKey segKey,
			boolean tagEverySegment, byte[] bufferedTag) {
		this.in = in;
		this.tagCipher = tagCipher;
		this.segCipher = segCipher;
		this.tagKey = tagKey;
		this.segKey = segKey;
		this.tagEverySegment = tagEverySegment;
		this.bufferedTag = bufferedTag;
		blockSize = segCipher.getBlockSize();
		if(blockSize < FRAME_HEADER_LENGTH)
			throw new IllegalArgumentException();
		iv = IvEncoder.encodeIv(0L, blockSize);
		ciphertext = new byte[MAX_SEGMENT_LENGTH];
	}

	public boolean readSegment(Segment s) throws IOException {
		boolean expectTag = tagEverySegment || firstSegment;
		firstSegment = false;
		try {
			if(expectTag) {
				// Read the tag if we don't have one buffered
				if(bufferedTag == null) {
					int offset = 0;
					while(offset < TAG_LENGTH) {
						int read = in.read(ciphertext, offset,
								TAG_LENGTH - offset);
						if(read == -1) {
							if(offset == 0) return false;
							throw new EOFException();
						}
						offset += read;
					}
					long seg = TagEncoder.decodeTag(ciphertext, tagCipher,
							tagKey);
					if(seg == -1) throw new FormatException();
					segmentNumber = seg;
				} else {
					long seg = TagEncoder.decodeTag(bufferedTag, tagCipher,
							tagKey);
					bufferedTag = null;
					if(seg == -1) throw new FormatException();
					segmentNumber = seg;
				}
			}
			// Read the first block of the frame
			int offset = 0;
			while(offset < blockSize) {
				int read = in.read(ciphertext, offset, blockSize - offset);
				if(read == -1) {
					if(offset == 0 && !expectTag) return false;
					throw new EOFException();
				}
				offset += read;
			}
			// Decrypt the first block of the frame
			byte[] plaintext = s.getBuffer();
			try {
				IvEncoder.updateIv(iv, segmentNumber);
				IvParameterSpec ivSpec = new IvParameterSpec(iv);
				segCipher.init(Cipher.DECRYPT_MODE, segKey, ivSpec);
				int decrypted = segCipher.update(ciphertext, 0, blockSize,
						plaintext);
				if(decrypted != blockSize) throw new RuntimeException();
			} catch(GeneralSecurityException badCipher) {
				throw new RuntimeException(badCipher);
			}
			// Parse the frame header
			int payload = HeaderEncoder.getPayloadLength(plaintext);
			int padding = HeaderEncoder.getPaddingLength(plaintext);
			int length = FRAME_HEADER_LENGTH + payload + padding + MAC_LENGTH;
			if(length > MAX_FRAME_LENGTH) throw new FormatException();
			// Read the remainder of the frame
			while(offset < length) {
				int read = in.read(ciphertext, offset, length - offset);
				if(read == -1) throw new EOFException();
				offset += read;
			}
			// Decrypt the remainder of the frame
			try {
				int decrypted = segCipher.doFinal(ciphertext, blockSize,
						length - blockSize, plaintext, blockSize);
				if(decrypted != length - blockSize)
					throw new RuntimeException();
			} catch(GeneralSecurityException badCipher) {
				throw new RuntimeException(badCipher);
			}
			s.setLength(length);
			s.setSegmentNumber(segmentNumber++);
			return true;
		} catch(IOException e) {
			segKey.erase();
			tagKey.erase();
			throw e;
		}
	}

	public int getMaxSegmentLength() {
		return MAX_SEGMENT_LENGTH - TAG_LENGTH;
	}
}