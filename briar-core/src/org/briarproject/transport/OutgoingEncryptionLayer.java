package org.briarproject.transport;

import static javax.crypto.Cipher.ENCRYPT_MODE;
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
	private final int frameLength, maxPayloadLength;

	private long capacity, frameNumber;
	private boolean writeTag;

	/** Constructor for the initiator's side of a connection. */
	OutgoingEncryptionLayer(OutputStream out, long capacity,
			AuthenticatedCipher frameCipher, SecretKey frameKey,
			int frameLength, byte[] tag) {
		this.out = out;
		this.capacity = capacity;
		this.frameCipher = frameCipher;
		this.frameKey = frameKey;
		this.frameLength = frameLength;
		this.tag = tag;
		maxPayloadLength = frameLength - HEADER_LENGTH - MAC_LENGTH;
		iv = new byte[IV_LENGTH];
		aad = new byte[AAD_LENGTH];
		ciphertext = new byte[frameLength];
		frameNumber = 0;
		writeTag = true;
	}

	/** Constructor for the responder's side of a connection. */
	OutgoingEncryptionLayer(OutputStream out, long capacity,
			AuthenticatedCipher frameCipher, SecretKey frameKey,
			int frameLength) {
		this.out = out;
		this.capacity = capacity;
		this.frameCipher = frameCipher;
		this.frameKey = frameKey;
		this.frameLength = frameLength;
		tag = null;
		maxPayloadLength = frameLength - HEADER_LENGTH - MAC_LENGTH;
		iv = new byte[IV_LENGTH];
		aad = new byte[AAD_LENGTH];
		ciphertext = new byte[frameLength];
		frameNumber = 0;
		writeTag = false;
	}

	public void writeFrame(byte[] frame, int payloadLength, boolean finalFrame)
			throws IOException {
		if(frameNumber > MAX_32_BIT_UNSIGNED) throw new IllegalStateException();
		// If the initiator's side of the connection is closed without writing
		// any data, don't write anything to the underlying transport
		if(writeTag && finalFrame && payloadLength == 0) return;
		// Write the tag if required
		if(writeTag) {
			try {
				out.write(tag, 0, tag.length);
			} catch(IOException e) {
				frameKey.erase();
				throw e;
			}
			capacity -= tag.length;
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
			throw e;
		}
		capacity -= ciphertextLength;
		frameNumber++;
	}

	public void flush() throws IOException {
		out.flush();
	}

	public long getRemainingCapacity() {
		// How many frame numbers can we use?
		long frameNumbers = MAX_32_BIT_UNSIGNED - frameNumber + 1;
		// How many full frames do we have space for?
		long bytes = writeTag ? capacity - tag.length : capacity;
		long fullFrames = bytes / frameLength;
		// Are we limited by frame numbers or space?
		if(frameNumbers > fullFrames) {
			// Can we send a partial frame after the full frames?
			int partialFrame = (int) (bytes - fullFrames * frameLength);
			if(partialFrame > HEADER_LENGTH + MAC_LENGTH) {
				// Send full frames and a partial frame, limited by space
				int partialPayload = partialFrame - HEADER_LENGTH - MAC_LENGTH;
				return maxPayloadLength * fullFrames + partialPayload;
			} else {
				// Send full frames only, limited by space
				return maxPayloadLength * fullFrames;
			}
		} else {
			// Send full frames only, limited by frame numbers
			return maxPayloadLength * frameNumbers;
		}
	}
}