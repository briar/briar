package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.util.Collection;
import java.util.Collections;

import javax.crypto.Mac;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.transport.ConnectionReader;

class ConnectionReaderImpl extends InputStream implements ConnectionReader {

	private final IncomingErrorCorrectionLayer in;
	private final Mac mac;
	private final boolean tolerateErrors;
	private final Frame frame;

	private long frameNumber = 0L;
	private int offset = 0, length = 0;

	ConnectionReaderImpl(IncomingErrorCorrectionLayer in, Mac mac,
			ErasableKey macKey, boolean tolerateErrors) {
		this.in = in;
		this.mac = mac;
		this.tolerateErrors = tolerateErrors;
		// Initialise the MAC
		try {
			mac.init(macKey);
		} catch(InvalidKeyException e) {
			throw new IllegalArgumentException(e);
		}
		macKey.erase();
		if(mac.getMacLength() != MAC_LENGTH)
			throw new IllegalArgumentException();
		frame = new Frame();
	}

	public InputStream getInputStream() {
		return this;
	}

	@Override
	public int read() throws IOException {
		while(length == 0) if(!readValidFrame()) return -1;
		int b = frame.getBuffer()[offset] & 0xff;
		offset++;
		length--;
		return b;
	}

	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		while(length == 0) if(!readValidFrame()) return -1;
		len = Math.min(len, length);
		System.arraycopy(frame.getBuffer(), offset, b, off, len);
		offset += len;
		length -= len;
		return len;
	}

	private boolean readValidFrame() throws IOException {
		while(true) {
			try {
				return readFrame();
			} catch(InvalidDataException e) {
				if(tolerateErrors) continue;
				throw new FormatException();
			}
		}
	}

	private boolean readFrame() throws IOException, InvalidDataException {
		assert length == 0;
		// Don't allow more than 2^32 frames to be read
		if(frameNumber > MAX_32_BIT_UNSIGNED)
			throw new IllegalStateException();
		// Read a frame
		Collection<Long> window = Collections.singleton(frameNumber);
		if(!in.readFrame(frame, window)) return false;
		// Check that the frame number is correct and the length is legal
		byte[] buf = frame.getBuffer();
		if(!HeaderEncoder.validateHeader(buf, frameNumber))
			throw new InvalidDataException();
		// Check that the payload and padding lengths are correct
		int payload = HeaderEncoder.getPayloadLength(buf);
		int padding = HeaderEncoder.getPaddingLength(buf);
		if(frame.getLength() != FRAME_HEADER_LENGTH + payload + padding
				+ MAC_LENGTH) throw new InvalidDataException();
		// Check that the padding is all zeroes
		int paddingStart = FRAME_HEADER_LENGTH + payload;
		for(int i = paddingStart; i < paddingStart + padding; i++) {
			if(buf[i] != 0) throw new InvalidDataException();
		}
		// Check the MAC
		int macStart = FRAME_HEADER_LENGTH + payload + padding;
		mac.update(buf, 0, macStart);
		byte[] expectedMac = mac.doFinal();
		for(int i = 0; i < expectedMac.length; i++) {
			if(expectedMac[i] != buf[macStart + i])
				throw new InvalidDataException();
		}
		offset = FRAME_HEADER_LENGTH;
		length = payload;
		frameNumber++;
		return true;
	}
}
