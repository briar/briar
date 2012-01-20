package net.sf.briar.transport;

import java.io.IOException;

class IncomingReliabilityLayerImpl implements IncomingReliabilityLayer {

	private final IncomingAuthenticationLayer in;
	private final FrameWindow window;
	private final int maxFrameLength;

	IncomingReliabilityLayerImpl(IncomingAuthenticationLayer in,
			FrameWindow window) {
		this.in = in;
		this.window = window;
		maxFrameLength = in.getMaxFrameLength();
	}

	public boolean readFrame(Frame f) throws IOException, InvalidDataException {
		if(!in.readFrame(f, window)) return false;
		long frameNumber = HeaderEncoder.getFrameNumber(f.getBuffer());
		if(!window.remove(frameNumber)) throw new IllegalStateException();
		return true;
	}

	public int getMaxFrameLength() {
		return maxFrameLength;
	}
}
