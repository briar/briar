package net.sf.briar.transport;

import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

/** A frame window that does not allow any reordering. */
class NullFrameWindow implements FrameWindow {

	private long base = 0L;

	public boolean isTooHigh(long frameNumber) {
		if(frameNumber < 0 || frameNumber > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		return frameNumber != base;
	}

	public boolean contains(long frameNumber) {
		if(frameNumber < 0 || frameNumber > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		return frameNumber == base;
	}

	public boolean remove(long frameNumber) {
		if(frameNumber < 0 || frameNumber > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		if(frameNumber != base) return false;
		base++;
		return true;
	}
}
