package org.briarproject.transport;

import static org.briarproject.api.transport.TransportConstants.AAD_LENGTH;
import static org.briarproject.api.transport.TransportConstants.HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.IV_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAC_LENGTH;
import static org.briarproject.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

import org.briarproject.api.crypto.AuthenticatedCipher;
import org.briarproject.api.crypto.SecretKey;

class OutgoingEncryptionLayer implements FrameWriter {

	private final OutputStream out;
	private final AuthenticatedCipher frameCipher;
	private final SecretKey frameKey;
	private final byte[] tag, iv, aad, ciphertext;
	private final int frameLength;

	private long frameNumber;
	private boolean writeTag;

	OutgoingEncryptionLayer(OutputStream out, AuthenticatedCipher frameCipher,
			SecretKey frameKey, int frameLength, byte[] tag) {
		this.out = out;
		this.frameCipher = frameCipher;
		this.frameKey = frameKey;
		this.frameLength = frameLength;
		this.tag = tag;
		iv = new byte[IV_LENGTH];
		aad = new byte[AAD_LENGTH];
		ciphertext = new byte[frameLength];
		frameNumber = 0;
		writeTag = true;
	}

	public void writeFrame(byte[] frame, int payloadLength, boolean finalFrame)
			throws IOException {
		if(frameNumber > MAX_32_BIT_UNSIGNED) throw new IllegalStateException();
		// Write the tag if required
		if(writeTag) {
			try {
				out.write(tag, 0, tag.length);
			} catch(IOException e) {
				frameKey.erase();
				throw e;
			}
			writeTag = false;
		}
		// Encode the header
		FrameEncoder.encodeHeader(frame, finalFrame, payloadLength);
		// Don't pad the final frame
		int plaintextLength, ciphertextLength;
		if(finalFrame) {
			plaintextLength = HEADER_LENGTH + payloadLength;
			ciphertextLength = plaintextLength + MAC_LENGTH;
		} else {
			plaintextLength = frameLength - MAC_LENGTH;
			ciphertextLength = frameLength;
		}
		// If there's any padding it must all be zeroes
		for(int i = HEADER_LENGTH + payloadLength; i < plaintextLength; i++) {
			frame[i] = 0;
		}
		// Encrypt and authenticate the frame
		FrameEncoder.encodeIv(iv, frameNumber);
		FrameEncoder.encodeAad(aad, frameNumber, plaintextLength);
		try {
			frameCipher.init(true, frameKey, iv, aad);
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
			throw e;
		}
		frameNumber++;
	}

	public void flush() throws IOException {
		// Write the tag if required
		if(writeTag) {
			try {
				out.write(tag, 0, tag.length);
			} catch(IOException e) {
				frameKey.erase();
				throw e;
			}
			writeTag = false;
		}
		out.flush();
	}
}