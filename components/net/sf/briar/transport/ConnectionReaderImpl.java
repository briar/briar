package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.util.Arrays;

import javax.crypto.Mac;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.transport.ConnectionReader;

class ConnectionReaderImpl extends FilterInputStream
implements ConnectionReader {

	private final ConnectionDecrypter decrypter;
	private final Mac mac;
	private final int maxPayloadLength;
	private final byte[] header, payload, footer;

	private long frame = 0L;
	private int payloadOff = 0, payloadLen = 0;
	private boolean betweenFrames = true;

	ConnectionReaderImpl(ConnectionDecrypter decrypter, Mac mac,
			ErasableKey macKey) {
		super(decrypter.getInputStream());
		this.decrypter = decrypter;
		this.mac = mac;
		// Initialise the MAC
		try {
			mac.init(macKey);
		} catch(InvalidKeyException e) {
			throw new IllegalArgumentException(e);
		}
		macKey.erase();
		maxPayloadLength =
			MAX_FRAME_LENGTH - FRAME_HEADER_LENGTH - mac.getMacLength();
		header = new byte[FRAME_HEADER_LENGTH];
		payload = new byte[maxPayloadLength];
		footer = new byte[mac.getMacLength()];
	}

	public InputStream getInputStream() {
		return this;
	}

	@Override
	public int read() throws IOException {
		if(betweenFrames && !readNonEmptyFrame()) return -1;
		int i = payload[payloadOff];
		payloadOff++;
		payloadLen--;
		if(payloadLen == 0) betweenFrames = true;
		return i;
	}

	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if(betweenFrames && !readNonEmptyFrame()) return -1;
		len = Math.min(len, payloadLen);
		System.arraycopy(payload, payloadOff, b, off, len);
		payloadOff += len;
		payloadLen -= len;
		if(payloadLen == 0) betweenFrames = true;
		return len;
	}

	private boolean readNonEmptyFrame() throws IOException {
		int payload = 0;
		do {
			payload = readFrame();
		} while(payload == 0);
		return payload > 0;
	}

	private int readFrame() throws IOException {
		assert betweenFrames;
		// Don't allow more than 2^32 frames to be read
		if(frame > MAX_32_BIT_UNSIGNED) throw new IllegalStateException();
		// Read the header
		int offset = 0;
		while(offset < header.length) {
			int read = in.read(header, offset, header.length - offset);
			if(read == -1) break;
			offset += read;
		}
		if(offset == 0) return -1; // EOF between frames
		if(offset < header.length) throw new EOFException(); // Unexpected EOF
		// Check that the frame number is correct and the length is legal
		if(!HeaderEncoder.validateHeader(header, frame, maxPayloadLength))
			throw new FormatException();
		payloadLen = HeaderEncoder.getPayloadLength(header);
		int paddingLen = HeaderEncoder.getPaddingLength(header);
		mac.update(header);
		// Read the payload
		offset = 0;
		while(offset < payloadLen) {
			int read = in.read(payload, offset, payloadLen - offset);
			if(read == -1) throw new EOFException(); // Unexpected EOF
			mac.update(payload, offset, read);
			offset += read;
		}
		payloadOff = 0;
		// Read the padding
		while(offset < payloadLen + paddingLen) {
			int read = in.read(payload, offset,
					payloadLen + paddingLen - offset);
			if(read == -1) throw new EOFException(); // Unexpected EOF
			mac.update(payload, offset, read);
			offset += read;
		}
		// Check that the padding is all zeroes
		for(int i = payloadLen; i < payloadLen + paddingLen; i++) {
			if(payload[i] != 0) throw new FormatException();
		}
		// Read the MAC
		byte[] expectedMac = mac.doFinal();
		decrypter.readMac(footer);
		if(!Arrays.equals(expectedMac, footer)) throw new FormatException();
		frame++;
		if(payloadLen > 0) betweenFrames = false;
		return payloadLen;
	}
}
