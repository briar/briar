package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import net.sf.briar.api.FormatException;

/** An encryption layer that performs no encryption. */
class NullIncomingEncryptionLayer implements FrameReader {

	private final InputStream in;

	NullIncomingEncryptionLayer(InputStream in) {
		this.in = in;
	}

	public int readFrame(byte[] frame) throws IOException {
		// Read the frame
		int ciphertextLength = 0;
		while(ciphertextLength < MAX_FRAME_LENGTH) {
			int read = in.read(frame, ciphertextLength,
					MAX_FRAME_LENGTH - ciphertextLength);
			if(read == -1) break; // We'll check the length later
			ciphertextLength += read;
		}
		int plaintextLength = ciphertextLength - MAC_LENGTH;
		if(plaintextLength < HEADER_LENGTH) throw new EOFException();
		// Decode and validate the header
		boolean lastFrame = FrameEncoder.isLastFrame(frame);
		if(!lastFrame && ciphertextLength < MAX_FRAME_LENGTH)
			throw new EOFException();
		int payloadLength = FrameEncoder.getPayloadLength(frame);
		if(payloadLength > plaintextLength - HEADER_LENGTH)
			throw new FormatException();
		// If there's any padding it must be all zeroes
		for(int i = HEADER_LENGTH + payloadLength; i < plaintextLength; i++)
			if(frame[i] != 0) throw new FormatException();
		return payloadLength;
	}
}
