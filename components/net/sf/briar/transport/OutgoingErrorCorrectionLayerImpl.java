package net.sf.briar.transport;

import java.io.IOException;

import net.sf.briar.api.transport.Segment;

class OutgoingErrorCorrectionLayerImpl implements OutgoingErrorCorrectionLayer {

	private final OutgoingEncryptionLayer out;
	private final ErasureEncoder encoder;
	private final int n;

	OutgoingErrorCorrectionLayerImpl(OutgoingEncryptionLayer out,
			ErasureEncoder encoder, int n) {
		this.out = out;
		this.encoder = encoder;
		this.n = n;
	}

	public void writeFrame(Frame f) throws IOException {
		for(Segment s : encoder.encodeFrame(f)) out.writeSegment(s);
	}

	public void flush() throws IOException {
		out.flush();
	}

	public long getRemainingCapacity() {
		return out.getRemainingCapacity() / n;
	}
}
