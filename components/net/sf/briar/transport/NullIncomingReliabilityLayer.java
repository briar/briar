package net.sf.briar.transport;

import java.io.IOException;

class NullIncomingReliabilityLayer implements IncomingReliabilityLayer {

	private final IncomingAuthenticationLayer in;
	private final FrameWindow window;

	NullIncomingReliabilityLayer(IncomingAuthenticationLayer in) {
		this.in = in;
		window = new NullFrameWindow();
	}

	public boolean readFrame(Frame f) throws IOException, InvalidDataException {
		if(!in.readFrame(f, window)) return false;
		long frameNumber = HeaderEncoder.getFrameNumber(f.getBuffer());
		if(!window.remove(frameNumber) && window.advance(frameNumber))
				throw new RuntimeException();
		return true;
	}
}
