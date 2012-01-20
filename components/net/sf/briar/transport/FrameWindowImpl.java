package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_WINDOW_SIZE;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.util.Collection;
import java.util.HashSet;

class FrameWindowImpl implements FrameWindow {

	private final Collection<Long> window;

	private long base;

	FrameWindowImpl() {
		window = new HashSet<Long>();
		fill(0, FRAME_WINDOW_SIZE);
		base = 0;
	}

	public boolean isTooHigh(long frameNumber) {
		if(frameNumber < 0 || frameNumber > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		return frameNumber >= base + FRAME_WINDOW_SIZE;
	}

	public boolean contains(long frameNumber) {
		if(frameNumber < 0 || frameNumber > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		return window.contains(frameNumber);
	}

	public boolean remove(long frameNumber) {
		if(frameNumber < 0 || frameNumber > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		if(!window.remove(frameNumber)) return false;
		if(frameNumber == base) {
			// Find the new base
			if(window.isEmpty()) {
				base += FRAME_WINDOW_SIZE;
				fill(base, base + FRAME_WINDOW_SIZE);
			} else {
				for(long l = base; l < base + FRAME_WINDOW_SIZE; l++) {
					if(window.contains(l)) {
						fill(base + FRAME_WINDOW_SIZE, l + FRAME_WINDOW_SIZE);
						base = l;
						break;
					}
				}
			}
		}
		return true;
	}

	private void fill(long from, long to) {
		for(long l = from; l < to; l++) {
			if(l <= MAX_32_BIT_UNSIGNED) window.add(l);
			else return;
		}
	}
}
