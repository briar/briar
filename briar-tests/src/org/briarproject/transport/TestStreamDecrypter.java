package org.briarproject.transport;

import org.briarproject.api.FormatException;
import org.briarproject.api.crypto.StreamDecrypter;
import org.briarproject.util.ByteUtils;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import static org.briarproject.api.transport.TransportConstants.HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAC_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_FRAME_LENGTH;

class TestStreamDecrypter implements StreamDecrypter {

	private final InputStream in;
	private final byte[] frame;

	TestStreamDecrypter(InputStream in) {
		this.in = in;
		frame = new byte[MAX_FRAME_LENGTH];
	}

	public int readFrame(byte[] payload) throws IOException {
		int offset = 0;
		while (offset < HEADER_LENGTH) {
			int read = in.read(frame, offset, HEADER_LENGTH - offset);
			if (read == -1) throw new EOFException();
			offset += read;
		}
		boolean finalFrame = (frame[0] & 0x80) == 0x80;
		int payloadLength = ByteUtils.readUint16(frame, 0) & 0x7FFF;
		while (offset < frame.length) {
			int read = in.read(frame, offset, frame.length - offset);
			if (read == -1) break;
			offset += read;
		}
		if (!finalFrame && offset < frame.length) throw new EOFException();
		if (offset < HEADER_LENGTH + payloadLength + MAC_LENGTH)
			throw new FormatException();
		System.arraycopy(frame, HEADER_LENGTH, payload, 0, payloadLength);
		return payloadLength;
	}
}
