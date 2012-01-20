package net.sf.briar.transport;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.transport.Segment;

class IncomingErrorCorrectionLayerImpl implements IncomingErrorCorrectionLayer {

	private final IncomingEncryptionLayer in;
	private final ErasureDecoder decoder;
	private final int n, k;
	private final Map<Long, Integer> discardCounts;
	private final Map<Long, Segment[]> segmentSets;

	IncomingErrorCorrectionLayerImpl(IncomingEncryptionLayer in,
			ErasureDecoder decoder, int n, int k) {
		this.in = in;
		this.decoder = decoder;
		this.n = n;
		this.k = k;
		discardCounts = new HashMap<Long, Integer>();
		segmentSets = new HashMap<Long, Segment[]>();
	}

	public boolean readFrame(Frame f, FrameWindow window) throws IOException,
	InvalidDataException {
		// Free any segment sets that have been removed from the window
		Iterator<Long> it = segmentSets.keySet().iterator();
		while(it.hasNext()) if(!window.contains(it.next())) it.remove();
		// Free any discard counts that are no longer too high for the window
		Iterator<Long> it1 = discardCounts.keySet().iterator();
		while(it1.hasNext()) if(!window.isTooHigh(it1.next())) it1.remove();
		// FIXME: Unnecessary allocation
		Segment s = new SegmentImpl();
		// Read segments until a frame can be decoded
		while(true) {
			// Read segments until a segment in the window is returned
			long frameNumber;
			while(true) {
				if(!in.readSegment(s)) return false;
				frameNumber = s.getSegmentNumber() / n;
				if(window.contains(frameNumber)) break;
				if(window.isTooHigh(frameNumber)) countDiscard(frameNumber);
			}
			// Add the segment to its segment set, or create one if necessary
			Segment[] set = segmentSets.get(frameNumber);
			if(set == null) {
				set = new Segment[n];
				segmentSets.put(frameNumber, set);
			}
			set[(int) (frameNumber % n)] = s;
			// Try to decode the frame
			if(decoder.decodeFrame(f, set)) return true;
		}
	}

	private void countDiscard(long frameNumber) throws FormatException {
		Integer count = discardCounts.get(frameNumber);
		if(count == null) discardCounts.put(frameNumber, 1);
		else if(count == n - k) throw new FormatException();
		else discardCounts.put(frameNumber, count + 1);
	}
}
