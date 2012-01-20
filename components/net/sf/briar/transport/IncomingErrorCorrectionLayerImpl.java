package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.transport.Segment;

class IncomingErrorCorrectionLayerImpl implements IncomingErrorCorrectionLayer {

	private final IncomingEncryptionLayer in;
	private final ErasureDecoder decoder;
	private final int n, k, maxSegmentLength, maxFrameLength;
	private final Map<Long, Integer> discardCounts;
	private final Map<Long, Segment[]> segmentSets;
	private final ArrayList<Segment> freeSegments;

	IncomingErrorCorrectionLayerImpl(IncomingEncryptionLayer in,
			ErasureDecoder decoder, int n, int k) {
		this.in = in;
		this.decoder = decoder;
		this.n = n;
		this.k = k;
		maxSegmentLength = in.getMaxSegmentLength();
		maxFrameLength = Math.min(MAX_FRAME_LENGTH, maxSegmentLength * k);
		discardCounts = new HashMap<Long, Integer>();
		segmentSets = new HashMap<Long, Segment[]>();
		freeSegments = new ArrayList<Segment>();
	}

	public boolean readFrame(Frame f, FrameWindow window) throws IOException,
	InvalidDataException {
		// Free any segment sets that have been removed from the window
		Iterator<Entry<Long, Segment[]>> it = segmentSets.entrySet().iterator();
		while(it.hasNext()) {
			Entry<Long, Segment[]> e = it.next();
			if(!window.contains(e.getKey())) {
				it.remove();
				for(Segment s : e.getValue()) if(s != null) freeSegments.add(s);
			}
		}
		// Free any discard counts that are no longer too high for the window
		Iterator<Long> it1 = discardCounts.keySet().iterator();
		while(it1.hasNext()) if(!window.isTooHigh(it1.next())) it1.remove();
		// Grab a free segment, or allocate one if necessary
		Segment s;
		int free = freeSegments.size();
		if(free == 0) s = new SegmentImpl(maxSegmentLength);
		else s = freeSegments.remove(free - 1);
		// Read segments until a frame can be decoded
		while(true) {
			// Read segments until a segment in the window is returned
			long frameNumber;
			while(true) {
				if(!in.readSegment(s)) {
					freeSegments.add(s);
					return false;
				}
				frameNumber = s.getSegmentNumber() / n;
				if(window.contains(frameNumber)) break;
				if(window.isTooHigh(frameNumber)) countDiscard(frameNumber);
			}
			// Add the segment to its set, creating a set if necessary
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

	public int getMaxFrameLength() {
		return maxFrameLength;
	}

	private void countDiscard(long frameNumber) throws FormatException {
		Integer count = discardCounts.get(frameNumber);
		if(count == null) discardCounts.put(frameNumber, 1);
		else if(count == n - k) throw new FormatException();
		else discardCounts.put(frameNumber, count + 1);
	}

	// Only for testing
	Map<Long, Segment[]> getSegmentSets() {
		return segmentSets;
	}

	// Only for testing
	Map<Long, Integer> getDiscardCounts() {
		return discardCounts;
	}
}
