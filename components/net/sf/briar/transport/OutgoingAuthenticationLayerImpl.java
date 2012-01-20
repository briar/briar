package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;

import java.io.IOException;
import java.security.InvalidKeyException;

import javax.crypto.Mac;
import javax.crypto.ShortBufferException;

import net.sf.briar.api.crypto.ErasableKey;

class OutgoingAuthenticationLayerImpl implements OutgoingAuthenticationLayer {

	private final OutgoingErrorCorrectionLayer out;
	private final Mac mac;
	private final int maxFrameLength;

	OutgoingAuthenticationLayerImpl(OutgoingErrorCorrectionLayer out, Mac mac,
			ErasableKey macKey) {
		this.out = out;
		this.mac = mac;
		try {
			mac.init(macKey);
		} catch(InvalidKeyException badKey) {
			throw new IllegalArgumentException(badKey);
		}
		macKey.erase();
		if(mac.getMacLength() != MAC_LENGTH)
			throw new IllegalArgumentException();
		maxFrameLength = out.getMaxFrameLength();
	}

	public void writeFrame(Frame f) throws IOException {
		byte[] buf = f.getBuffer();
		int length = f.getLength() - MAC_LENGTH;
		mac.update(buf, 0, length);
		try {
			mac.doFinal(buf, length);
		} catch(ShortBufferException badMac) {
			throw new RuntimeException(badMac);
		}
		out.writeFrame(f);
	}

	public void flush() throws IOException {
		out.flush();
	}

	public long getRemainingCapacity() {
		return out.getRemainingCapacity();
	}

	public int getMaxFrameLength() {
		return maxFrameLength;
	}
}
