package org.briarproject.transport;

import org.briarproject.api.crypto.StreamEncrypter;
import org.briarproject.util.ByteUtils;

import java.io.IOException;
import java.io.OutputStream;

import static org.briarproject.api.transport.TransportConstants.HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAC_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_FRAME_LENGTH;

class TestStreamEncrypter implements StreamEncrypter {

	private final OutputStream out;
	private final byte[] tag, frame;

	private boolean writeTag = true;

	TestStreamEncrypter(OutputStream out, byte[] tag) {
		this.out = out;
		this.tag = tag;
		frame = new byte[MAX_FRAME_LENGTH];
	}

	public void writeFrame(byte[] payload, int payloadLength,
			int paddingLength, boolean finalFrame) throws IOException {
		if (writeTag) {
			out.write(tag);
			writeTag = false;
		}
		ByteUtils.writeUint16(payloadLength, frame, 0);
		if (finalFrame) frame[0] |= 0x80;
		System.arraycopy(payload, 0, frame, HEADER_LENGTH, payloadLength);
		for (int i = HEADER_LENGTH + payloadLength; i < frame.length; i++)
			frame[i] = 0;
		if (finalFrame)
			out.write(frame, 0, HEADER_LENGTH + payloadLength + MAC_LENGTH);
		else out.write(frame, 0, frame.length);
	}

	public void flush() throws IOException {
		out.flush();
	}
}
