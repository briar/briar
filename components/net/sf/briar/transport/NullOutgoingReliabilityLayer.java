package net.sf.briar.transport;

import java.io.IOException;

class NullOutgoingReliabilityLayer implements OutgoingReliabilityLayer {

	private final OutgoingAuthenticationLayer out;
	private final int maxFrameLength;

	NullOutgoingReliabilityLayer(OutgoingAuthenticationLayer out) {
		this.out = out;
		maxFrameLength = out.getMaxFrameLength();
	}

	public void writeFrame(Frame f) throws IOException {
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
