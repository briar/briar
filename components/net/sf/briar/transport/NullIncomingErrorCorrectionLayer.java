package net.sf.briar.transport;

import java.io.IOException;
import java.util.Collection;

import net.sf.briar.api.transport.Segment;

class NullIncomingErrorCorrectionLayer implements IncomingErrorCorrectionLayer {

	private final IncomingEncryptionLayer in;
	private final Segment segment;

	NullIncomingErrorCorrectionLayer(IncomingEncryptionLayer in) {
		this.in = in;
		segment = new SegmentImpl();
	}

	public boolean readFrame(Frame f, Collection<Long> window)
	throws IOException, InvalidDataException {
		while(true) {
			if(!in.readSegment(segment)) return false;
			byte[] buf = segment.getBuffer();
			if(window.contains(HeaderEncoder.getFrameNumber(buf))) break;
		}
		int length = segment.getLength();
		// FIXME: Unnecessary copy
		System.arraycopy(segment.getBuffer(), 0, f.getBuffer(), 0, length);
		f.setLength(length);
		return true;
	}
}
