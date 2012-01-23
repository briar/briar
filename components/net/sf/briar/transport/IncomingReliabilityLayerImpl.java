package net.sf.briar.transport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.ListIterator;

/** A reliability layer that reorders out-of-order frames. */
class IncomingReliabilityLayerImpl implements IncomingReliabilityLayer {

	private final IncomingAuthenticationLayer in;
	private final int maxFrameLength;
	private final FrameWindow window;
	private final LinkedList<Frame> frames; // Ordered by frame number
	private final ArrayList<Frame> freeFrames;

	private long nextFrameNumber = 0L;

	IncomingReliabilityLayerImpl(IncomingAuthenticationLayer in) {
		this.in = in;
		maxFrameLength = in.getMaxFrameLength();
		window = new FrameWindowImpl();
		frames = new LinkedList<Frame>();
		freeFrames = new ArrayList<Frame>();
	}

	public Frame readFrame(Frame f) throws IOException,
	InvalidDataException {
		freeFrames.add(f);
		// Read frames until there's an in-order frame to return
		Frame next = frames.peek();
		while(next == null || next.getFrameNumber() > nextFrameNumber) {
			// Grab a free frame, or allocate one if necessary
			int free = freeFrames.size();
			if(free == 0) f = new Frame(maxFrameLength);
			else f = freeFrames.remove(free - 1);
			// Read a frame
			if(!in.readFrame(f, window)) return null;
			// If the frame is in order, return it
			long frameNumber = f.getFrameNumber();
			if(frameNumber == nextFrameNumber) {
				if(!window.remove(nextFrameNumber))
					throw new IllegalStateException();
				nextFrameNumber++;
				return f;
			}
			// Insert the frame into the list
			if(next == null || next.getFrameNumber() > frameNumber) {
				frames.push(f);
			} else {
				boolean inserted = false;
				ListIterator<Frame> it = frames.listIterator();
				while(it.hasNext()) {
					if(it.next().getFrameNumber() > frameNumber) {
						// Insert the frame before the one just examined
						it.previous();
						it.add(f);
						inserted = true;
						break;
					}
				}
				if(!inserted) frames.add(f);
			}
			next = frames.peek();
		}
		frames.poll();
		if(!window.remove(nextFrameNumber)) throw new IllegalStateException();
		nextFrameNumber++;
		return next;
	}

	public int getMaxFrameLength() {
		return maxFrameLength;
	}

	// Only for testing
	public int getFreeFramesCount() {
		return freeFrames.size();
	}
}
