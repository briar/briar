package net.sf.briar.transport;

import java.io.IOException;

import net.sf.briar.api.transport.Segment;

class NullIncomingErrorCorrectionLayer implements IncomingErrorCorrectionLayer {

	private final IncomingEncryptionLayer in;
	private final int maxFrameLength;
	private final Segment segment;

	NullIncomingErrorCorrectionLayer(IncomingEncryptionLayer in) {
		this.in = in;
		maxFrameLength = in.getMaxSegmentLength();
		segment = new SegmentImpl(maxFrameLength);
	}

	public boolean readFrame(Frame f, FrameWindow window) throws IOException,
	InvalidDataException {
		while(true) {
			if(!in.readSegment(segment)) return false;
			byte[] buf = segment.getBuffer();
			long frameNumber = HeaderEncoder.getFrameNumber(buf);
			if(window.contains(frameNumber)) break;
		}
		int length = segment.getLength();
		// FIXME: Unnecessary copy
		System.arraycopy(segment.getBuffer(), 0, f.getBuffer(), 0, length);
		f.setLength(length);
		return true;
	}

	public int getMaxFrameLength() {
		return maxFrameLength;
	}
}
