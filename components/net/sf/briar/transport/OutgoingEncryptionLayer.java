package net.sf.briar.transport;

import static javax.crypto.Cipher.ENCRYPT_MODE;
import static net.sf.briar.api.transport.TransportConstants.AAD_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.IV_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;

import net.sf.briar.api.crypto.AuthenticatedCipher;
import net.sf.briar.api.crypto.ErasableKey;

class OutgoingEncryptionLayer implements FrameWriter {

	private final OutputStream out;
	private final Cipher tagCipher;
	private final AuthenticatedCipher frameCipher;
	private final ErasableKey tagKey, frameKey;
	private final byte[] iv, aad, ciphertext;
	private final int maxFrameLength;

	private boolean writeTag;
	private long capacity, frameNumber;

	OutgoingEncryptionLayer(OutputStream out, long capacity, Cipher tagCipher,
			AuthenticatedCipher frameCipher, ErasableKey tagKey,
			ErasableKey frameKey, boolean writeTag, int maxFrameLength) {
		this.out = out;
		this.capacity = writeTag ? capacity - TAG_LENGTH : capacity;
		this.tagCipher = tagCipher;
		this.frameCipher = frameCipher;
		this.tagKey = tagKey;
		this.frameKey = frameKey;
		this.writeTag = writeTag;
		this.maxFrameLength = maxFrameLength;
		iv = new byte[IV_LENGTH];
		aad = new byte[AAD_LENGTH];
		ciphertext = new byte[maxFrameLength];
		frameNumber = 0L;
	}

	public void writeFrame(byte[] frame, int payloadLength, int paddingLength,
			boolean lastFrame) throws IOException {
		int plaintextLength = HEADER_LENGTH + payloadLength + paddingLength;
		int ciphertextLength = plaintextLength + MAC_LENGTH;
		if(ciphertextLength > maxFrameLength)
			throw new IllegalArgumentException();
		if(!lastFrame && ciphertextLength < maxFrameLength)
			throw new IllegalArgumentException();
		// If the initiator's side of the connection is closed without writing
		// any payload or padding, don't write a tag or an empty frame
		if(writeTag && lastFrame && payloadLength + paddingLength == 0) return;
		// Write the tag if required
		if(writeTag) {
			TagEncoder.encodeTag(ciphertext, tagCipher, tagKey);
			try {
				out.write(ciphertext, 0, TAG_LENGTH);
			} catch(IOException e) {
				frameKey.erase();
				tagKey.erase();
				throw e;
			}
			writeTag = false;
		}
		// Encode the header
		FrameEncoder.encodeHeader(frame, lastFrame, payloadLength);
		// If there's any padding it must all be zeroes
		for(int i = HEADER_LENGTH + payloadLength; i < plaintextLength; i++)
			frame[i] = 0;
		// Encrypt and authenticate the frame
		FrameEncoder.encodeIv(iv, frameNumber);
		FrameEncoder.encodeAad(aad, frameNumber, plaintextLength);
		try {
			frameCipher.init(ENCRYPT_MODE, frameKey, iv, aad);
			int encrypted = frameCipher.doFinal(frame, 0, plaintextLength,
					ciphertext, 0);
			if(encrypted != ciphertextLength) throw new RuntimeException();
		} catch(GeneralSecurityException badCipher) {
			throw new RuntimeException(badCipher);
		}
		// Write the frame
		try {
			out.write(ciphertext, 0, ciphertextLength);
		} catch(IOException e) {
			frameKey.erase();
			tagKey.erase();
			throw e;
		}
		capacity -= ciphertextLength;
		frameNumber++;
	}

	public void flush() throws IOException {
		out.flush();
	}

	public long getRemainingCapacity() {
		return capacity;
	}
}