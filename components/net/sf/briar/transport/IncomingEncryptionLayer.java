package net.sf.briar.transport;

import static javax.crypto.Cipher.DECRYPT_MODE;
import static net.sf.briar.api.transport.TransportConstants.AAD_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.IV_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.crypto.AuthenticatedCipher;
import net.sf.briar.api.crypto.ErasableKey;

class IncomingEncryptionLayer implements FrameReader {

	private final InputStream in;
	private final Cipher tagCipher;
	private final AuthenticatedCipher frameCipher;
	private final ErasableKey tagKey, frameKey;
	private final byte[] iv, aad, ciphertext;
	private final int frameLength;

	private long frameNumber;
	private boolean readTag, finalFrame;

	/** Constructor for the initiator's side of a connection. */
	IncomingEncryptionLayer(InputStream in, Cipher tagCipher,
			AuthenticatedCipher frameCipher, ErasableKey tagKey,
			ErasableKey frameKey, int frameLength) {
		this.in = in;
		this.tagCipher = tagCipher;
		this.frameCipher = frameCipher;
		this.tagKey = tagKey;
		this.frameKey = frameKey;
		this.frameLength = frameLength;
		iv = new byte[IV_LENGTH];
		aad = new byte[AAD_LENGTH];
		ciphertext = new byte[frameLength];
		frameNumber = 0L;
		readTag = true;
		finalFrame = false;
	}

	/** Constructor for the responder's side of a connection. */
	IncomingEncryptionLayer(InputStream in, AuthenticatedCipher frameCipher,
			ErasableKey frameKey, int frameLength) {
		this.in = in;
		this.frameCipher = frameCipher;
		this.frameKey = frameKey;
		this.frameLength = frameLength;
		tagCipher = null;
		tagKey = null;
		iv = new byte[IV_LENGTH];
		aad = new byte[AAD_LENGTH];
		ciphertext = new byte[frameLength];
		frameNumber = 0L;
		readTag = false;
		finalFrame = false;
	}

	public int readFrame(byte[] frame) throws IOException {
		if(finalFrame) return -1;
		// Read the tag if required
		if(readTag) {
			int offset = 0;
			try {
				while(offset < TAG_LENGTH) {
					int read = in.read(ciphertext, offset, TAG_LENGTH - offset);
					if(read == -1) throw new EOFException();
					offset += read;
				}
			} catch(IOException e) {
				frameKey.erase();
				tagKey.erase();
				throw e;
			}
			if(!TagEncoder.decodeTag(ciphertext, tagCipher, tagKey))
				throw new FormatException();
			readTag = false;
		}
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
			tagKey.erase();
			throw e;
		}
		int plaintextLength = ciphertextLength - MAC_LENGTH;
		if(plaintextLength < HEADER_LENGTH) throw new EOFException();
		// Decrypt and authenticate the frame
		FrameEncoder.encodeIv(iv, frameNumber);
		FrameEncoder.encodeAad(aad, frameNumber, plaintextLength);
		try {
			frameCipher.init(DECRYPT_MODE, frameKey, iv, aad);
			int decrypted = frameCipher.doFinal(ciphertext, 0, ciphertextLength,
					frame, 0);
			if(decrypted != plaintextLength) throw new RuntimeException();
		} catch(GeneralSecurityException badCipher) {
			throw new RuntimeException(badCipher);
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