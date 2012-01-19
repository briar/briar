package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.Collection;

import javax.crypto.Mac;

import net.sf.briar.api.crypto.ErasableKey;

class IncomingAuthenticationLayerImpl implements IncomingAuthenticationLayer {

	private final IncomingErrorCorrectionLayer in;
	private final Mac mac;

	IncomingAuthenticationLayerImpl(IncomingErrorCorrectionLayer in, Mac mac,
			ErasableKey macKey) {
		this.in = in;
		this.mac = mac;
		// Initialise the MAC
		try {
			mac.init(macKey);
		} catch(InvalidKeyException e) {
			throw new IllegalArgumentException(e);
		}
		macKey.erase();
		if(mac.getMacLength() != MAC_LENGTH)
			throw new IllegalArgumentException();
	}

	public boolean readFrame(Frame f, Collection<Long> window)
	throws IOException, InvalidDataException {
		// Read a frame
		if(!in.readFrame(f, window)) return false;
		// Check that the length is legal
		byte[] buf = f.getBuffer();
		long frameNumber = HeaderEncoder.getFrameNumber(buf);
		if(!HeaderEncoder.validateHeader(buf, frameNumber))
			throw new InvalidDataException();
		// Check that the payload and padding lengths are correct
		int payload = HeaderEncoder.getPayloadLength(buf);
		int padding = HeaderEncoder.getPaddingLength(buf);
		if(f.getLength() != FRAME_HEADER_LENGTH + payload + padding
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
		frameNumber++;
		return true;
	}
}
