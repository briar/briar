package net.sf.briar.transport;

import java.io.IOException;

class NullOutgoingReliabilityLayer implements OutgoingReliabilityLayer {

	private final OutgoingAuthenticationLayer out;

	NullOutgoingReliabilityLayer(OutgoingAuthenticationLayer out) {
		this.out = out;
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
}
