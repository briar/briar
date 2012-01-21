package net.sf.briar.transport;

import java.io.IOException;

class NullIncomingReliabilityLayer implements IncomingReliabilityLayer {

	private final IncomingAuthenticationLayer in;
	private final int maxFrameLength;
	private final FrameWindow window;

	NullIncomingReliabilityLayer(IncomingAuthenticationLayer in) {
		this.in = in;
		maxFrameLength = in.getMaxFrameLength();
		window = new NullFrameWindow();
	}

	public Frame readFrame(Frame f) throws IOException, InvalidDataException {
		if(!in.readFrame(f, window)) return null;
		if(!window.remove(f.getFrameNumber()))
			throw new IllegalStateException();
		return f;
	}

	public int getMaxFrameLength() {
		return maxFrameLength;
	}
}
