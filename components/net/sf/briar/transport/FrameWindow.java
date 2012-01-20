package net.sf.briar.transport;

interface FrameWindow {

	/** Returns true if the given number is too high to fit in the window. */
	boolean isTooHigh(long frameNumber);

	/** Returns true if the given number is in the window. */
	boolean contains(long frameNumber);

	/**
	 * Removes the given number from the window and advances the window.
	 * Returns false if the given number is not in the window.
	 */
	boolean remove(long frameNumber);
}
