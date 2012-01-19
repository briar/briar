package net.sf.briar.transport;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

class NullIncomingReliabilityLayer implements IncomingReliabilityLayer {

	private final IncomingAuthenticationLayer in;

	private long frameNumber = 0L;

	NullIncomingReliabilityLayer(IncomingAuthenticationLayer in) {
		this.in = in;
	}

	public boolean readFrame(Frame f) throws IOException, InvalidDataException {
		// Frames must be read in order
		Collection<Long> window = Collections.singleton(frameNumber);
		if(!in.readFrame(f, window)) return false;
		frameNumber++;
		return true;
	}
}
