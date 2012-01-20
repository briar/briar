package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import java.io.IOException;

import net.sf.briar.api.transport.Segment;

class OutgoingErrorCorrectionLayerImpl implements OutgoingErrorCorrectionLayer {

	private final OutgoingEncryptionLayer out;
	private final ErasureEncoder encoder;
	private final int n, maxFrameLength;

	OutgoingErrorCorrectionLayerImpl(OutgoingEncryptionLayer out,
			ErasureEncoder encoder, int n, int k) {
		this.out = out;
		this.encoder = encoder;
		this.n = n;
		maxFrameLength = Math.min(MAX_FRAME_LENGTH,
				out.getMaxSegmentLength() * k);
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

	public int getMaxFrameLength() {
		return maxFrameLength;
	}
}
