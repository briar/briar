package org.briarproject.transport;

import static org.briarproject.api.transport.TransportConstants.AAD_LENGTH;
import static org.briarproject.api.transport.TransportConstants.HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.IV_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAC_LENGTH;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

import org.briarproject.api.FormatException;
import org.briarproject.api.crypto.AuthenticatedCipher;
import org.briarproject.api.crypto.SecretKey;

class IncomingEncryptionLayer implements FrameReader {

	private final InputStream in;
	private final AuthenticatedCipher frameCipher;
	private final SecretKey frameKey;
	private final byte[] iv, aad, ciphertext;
	private final int frameLength;

	private long frameNumber;
	private boolean finalFrame;

	IncomingEncryptionLayer(InputStream in, AuthenticatedCipher frameCipher,
			SecretKey frameKey, int frameLength) {
		this.in = in;
		this.frameCipher = frameCipher;
		this.frameKey = frameKey;
		this.frameLength = frameLength;
		iv = new byte[IV_LENGTH];
		aad = new byte[AAD_LENGTH];
		ciphertext = new byte[frameLength];
		frameNumber = 0;
		finalFrame = false;
	}

	public int readFrame(byte[] frame) throws IOException {
		if(finalFrame) return -1;
		// Read the frame
		int ciphertextLength = 0;
		try {
			while(ciphertextLength < frameLength) {
				int read = in.read(ciphertext, ciphertextLength,
						frameLength - ciphertextLength);
				if(read == -1) break; // We'll check the length later
				ciphertextLength += read;
			}
		} catch(IOException e) {
			frameKey.erase();
			throw e;
		}
		int plaintextLength = ciphertextLength - MAC_LENGTH;
		if(plaintextLength < HEADER_LENGTH) throw new EOFException();
		// Decrypt and authenticate the frame
		FrameEncoder.encodeIv(iv, frameNumber);
		FrameEncoder.encodeAad(aad, frameNumber, plaintextLength);
		try {
			frameCipher.init(false, frameKey, iv, aad);
			int decrypted = frameCipher.doFinal(ciphertext, 0, ciphertextLength,
					frame, 0);
			if(decrypted != plaintextLength) throw new RuntimeException();
		} catch(GeneralSecurityException e) {
			throw new FormatException();
		}
		// Decode and validate the header
		finalFrame = FrameEncoder.isFinalFrame(frame);
		if(!finalFrame && ciphertextLength < frameLength)
			throw new FormatException();
		int payloadLength = FrameEncoder.getPayloadLength(frame);
		if(payloadLength > plaintextLength - HEADER_LENGTH)
			throw new FormatException();
		// If there's any padding it must be all zeroes
		for(int i = HEADER_LENGTH + payloadLength; i < plaintextLength; i++) {
			if(frame[i] != 0) throw new FormatException();
		}
		frameNumber++;
		return payloadLength;
	}
}