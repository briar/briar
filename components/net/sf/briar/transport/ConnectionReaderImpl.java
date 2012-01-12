package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;

import javax.crypto.Mac;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.transport.ConnectionReader;

class ConnectionReaderImpl extends InputStream implements ConnectionReader {

	private final ConnectionDecrypter decrypter;
	private final Mac mac;
	private final int macLength;
	private final byte[] buf;

	private long frame = 0L;
	private int bufOffset = 0, bufLength = 0;

	ConnectionReaderImpl(ConnectionDecrypter decrypter, Mac mac,
			ErasableKey macKey) {
		this.decrypter = decrypter;
		this.mac = mac;
		// Initialise the MAC
		try {
			mac.init(macKey);
		} catch(InvalidKeyException e) {
			throw new IllegalArgumentException(e);
		}
		macKey.erase();
		macLength = mac.getMacLength();
		buf = new byte[MAX_FRAME_LENGTH];
	}

	public InputStream getInputStream() {
		return this;
	}

	@Override
	public int read() throws IOException {
		while(bufLength == 0) if(!readFrame()) return -1;
		int b = buf[bufOffset] & 0xff;
		bufOffset++;
		bufLength--;
		return b;
	}

	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		while(bufLength == 0) if(!readFrame()) return -1;
		len = Math.min(len, bufLength);
		System.arraycopy(buf, bufOffset, b, off, len);
		bufOffset += len;
		bufLength -= len;
		return len;
	}

	private boolean readFrame() throws IOException {
		assert bufLength == 0;
		// Don't allow more than 2^32 frames to be read
		if(frame > MAX_32_BIT_UNSIGNED) throw new IllegalStateException();
		// Read a frame
		int length = decrypter.readFrame(buf);
		if(length == -1) return false;
		// Check that the frame number is correct and the length is legal
		int max = MAX_FRAME_LENGTH - FRAME_HEADER_LENGTH - macLength;
		if(!HeaderEncoder.validateHeader(buf, frame, max))
			throw new FormatException();
		int payload = HeaderEncoder.getPayloadLength(buf);
		int padding = HeaderEncoder.getPaddingLength(buf);
		if(length != FRAME_HEADER_LENGTH + payload + padding + macLength)
			throw new FormatException();
		// Check that the padding is all zeroes
		int paddingStart = FRAME_HEADER_LENGTH + payload;
		for(int i = paddingStart; i < paddingStart + padding; i++) {
			if(buf[i] != 0) throw new FormatException();
		}
		// Check the MAC
		int macStart = FRAME_HEADER_LENGTH + payload + padding;
		mac.update(buf, 0, macStart);
		byte[] expectedMac = mac.doFinal();
		for(int i = 0; i < macLength; i++) {
			if(expectedMac[i] != buf[macStart + i]) throw new FormatException();
		}
		bufOffset = FRAME_HEADER_LENGTH;
		bufLength = payload;
		frame++;
		return true;
	}
}
