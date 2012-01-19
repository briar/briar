package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_WINDOW_SIZE;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

class FrameWindowImpl implements FrameWindow {

	private final Collection<Long> window;

	private long centre;

	FrameWindowImpl() {
		window = new HashSet<Long>();
		for(long l = 0; l < FRAME_WINDOW_SIZE / 2; l++) window.add(l);
		centre = 0;
	}

	public boolean contains(long frameNumber) {
		if(frameNumber < 0 || frameNumber > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		return window.contains(frameNumber);
	}

	public boolean advance(long frameNumber) {
		if(frameNumber < 0 || frameNumber > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		if(frameNumber <= centre) return true;
		// Remove values that have passed out of the window
		long newBottom = getBottom(frameNumber);
		Iterator<Long> it = window.iterator();
		while(it.hasNext()) if(it.next() < newBottom) it.remove();
		// Add values that have passed into the window
		long fillFrom = Math.max(newBottom, getTop(centre) + 1);
		long newTop = getTop(frameNumber);
		for(long l = fillFrom; l <= newTop; l++) window.add(l);
		centre = frameNumber;
		return true;
	}

	public boolean remove(long frameNumber) {
		if(frameNumber < 0 || frameNumber > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		if(!window.remove(frameNumber)) return false;
		if(frameNumber >= centre && frameNumber < MAX_32_BIT_UNSIGNED)
			advance(frameNumber + 1);
		return true;
	}

	// Returns the lowest value contained in a window with the given centre
	private static long getBottom(long centre) {
		return Math.max(0, centre - FRAME_WINDOW_SIZE / 2);
	}

	// Returns the highest value contained in a window with the given centre
	private static long getTop(long centre) {
		return Math.min(MAX_32_BIT_UNSIGNED,
				centre + FRAME_WINDOW_SIZE / 2 - 1);
	}
}
