package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import java.io.IOException;
import java.security.InvalidKeyException;

import javax.crypto.Mac;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.crypto.ErasableKey;

class IncomingAuthenticationLayerImpl implements FrameReader {

	private final FrameReader in;
	private final Mac mac;

	IncomingAuthenticationLayerImpl(FrameReader in, Mac mac,
			ErasableKey macKey) {
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
	}

	public boolean readFrame(Frame f) throws IOException {
		// Read a frame
		if(!in.readFrame(f)) return false;
		// Check that the length is legal
		int length = f.getLength();
		if(length < FRAME_HEADER_LENGTH + MAC_LENGTH)
			throw new FormatException();
		if(length > MAX_FRAME_LENGTH) throw new FormatException();
		// Check that the payload and padding lengths are correct
		byte[] buf = f.getBuffer();
		int payload = HeaderEncoder.getPayloadLength(buf);
		int padding = HeaderEncoder.getPaddingLength(buf);
		if(length != FRAME_HEADER_LENGTH + payload + padding + MAC_LENGTH)
			throw new FormatException();
		// Check that the padding is all zeroes
		int paddingStart = FRAME_HEADER_LENGTH + payload;
		for(int i = paddingStart; i < paddingStart + padding; i++) {
			if(buf[i] != 0) throw new FormatException();
		}
		// Verify the MAC
		int macStart = FRAME_HEADER_LENGTH + payload + padding;
		mac.update(buf, 0, macStart);
		byte[] expectedMac = mac.doFinal();
		for(int i = 0; i < expectedMac.length; i++) {
			if(expectedMac[i] != buf[macStart + i]) throw new FormatException();
		}
		return true;
	}
}
