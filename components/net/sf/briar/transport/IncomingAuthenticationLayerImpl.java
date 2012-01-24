package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.ACK_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;

import java.io.IOException;
import java.security.InvalidKeyException;

import javax.crypto.Mac;

import net.sf.briar.api.crypto.ErasableKey;

class IncomingAuthenticationLayerImpl implements IncomingAuthenticationLayer {

	private final IncomingErrorCorrectionLayer in;
	private final Mac mac;
	private final int headerLength, maxFrameLength;

	IncomingAuthenticationLayerImpl(IncomingErrorCorrectionLayer in, Mac mac,
			ErasableKey macKey, boolean ackHeader) {
		this.in = in;
		this.mac = mac;
		try {
			mac.init(macKey);
		} catch(InvalidKeyException e) {
			throw new IllegalArgumentException(e);
		}
		macKey.erase();
		if(mac.getMacLength() != MAC_LENGTH)
			throw new IllegalArgumentException();
		if(ackHeader) headerLength = FRAME_HEADER_LENGTH + ACK_HEADER_LENGTH;
		else headerLength = FRAME_HEADER_LENGTH;
		maxFrameLength = in.getMaxFrameLength();
	}

	public boolean readFrame(Frame f, FrameWindow window) throws IOException,
	InvalidDataException {
		// Read a frame
		if(!in.readFrame(f, window)) return false;
		// Check that the length is legal
		int length = f.getLength();
		if(length < headerLength + MAC_LENGTH)
			throw new InvalidDataException();
		if(length > maxFrameLength) throw new InvalidDataException();
		// Check that the payload and padding lengths are correct
		byte[] buf = f.getBuffer();
		int payload = HeaderEncoder.getPayloadLength(buf);
		int padding = HeaderEncoder.getPaddingLength(buf);
		if(length != headerLength + payload + padding + MAC_LENGTH)
			throw new InvalidDataException();
		// Check that the padding is all zeroes
		int paddingStart = headerLength + payload;
		for(int i = paddingStart; i < paddingStart + padding; i++) {
			if(buf[i] != 0) throw new InvalidDataException();
		}
		// Verify the MAC
		int macStart = headerLength + payload + padding;
		mac.update(buf, 0, macStart);
		byte[] expectedMac = mac.doFinal();
		for(int i = 0; i < expectedMac.length; i++) {
			if(expectedMac[i] != buf[macStart + i])
				throw new InvalidDataException();
		}
		return true;
	}

	public int getMaxFrameLength() {
		return maxFrameLength;
	}
}
