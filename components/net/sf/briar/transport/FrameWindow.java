package net.sf.briar.transport;

interface FrameWindow {

	/** Returns true if the given number is in the window. */
	boolean contains(long frameNumber);

	/**
	 * Advances the window so it is centred on the given number, unless the
	 * centre is already greater than the number. Returns false if the window
	 * is unmodifiable.
	 */
	boolean advance(long frameNumber);

	/**
	 * Removes the given number from the window and, if the number is greater
	 * than or equal to the window's centre, advances the centre to one greater
	 * than the given number. Returns false if the given number is not in the
	 * window or the window is unmodifiable.
	 */
	boolean remove(long frameNumber);
}
