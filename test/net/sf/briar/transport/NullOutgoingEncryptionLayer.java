package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;

import java.io.IOException;
import java.io.OutputStream;

/** An encryption layer that performs no encryption. */
class NullOutgoingEncryptionLayer implements FrameWriter {

	private final OutputStream out;

	private long capacity;

	NullOutgoingEncryptionLayer(OutputStream out) {
		this.out = out;
		capacity = Long.MAX_VALUE;
	}

	NullOutgoingEncryptionLayer(OutputStream out, long capacity) {
		this.out = out;
		this.capacity = capacity;
	}

	public void writeFrame(byte[] frame, int payloadLength, int paddingLength,
			boolean lastFrame) throws IOException {
		int plaintextLength = HEADER_LENGTH + payloadLength + paddingLength;
		int ciphertextLength = plaintextLength + MAC_LENGTH;
		// Encode the header
		FrameEncoder.encodeHeader(frame, lastFrame, payloadLength);
		// If there's any padding it must all be zeroes
		for(int i = HEADER_LENGTH + payloadLength; i < plaintextLength; i++)
			frame[i] = 0;
		// Write the frame
		out.write(frame, 0, ciphertextLength);
		capacity -= ciphertextLength;
	}

	public void flush() throws IOException {
		out.flush();
	}

	public long getRemainingCapacity() {
		return capacity;
	}
}
