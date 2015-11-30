package org.briarproject.transport;

import static org.briarproject.api.transport.TransportConstants.REORDERING_WINDOW_SIZE;
import static org.briarproject.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

// This class is not thread-safe
class ReorderingWindow {

	private final Set<Long> unseen;

	private long centre;

	ReorderingWindow() {
		unseen = new HashSet<Long>();
		for (long l = 0; l < REORDERING_WINDOW_SIZE / 2; l++) unseen.add(l);
		centre = 0;
	}

	ReorderingWindow(long centre, byte[] bitmap) {
		if (centre < 0 || centre > MAX_32_BIT_UNSIGNED + 1)
			throw new IllegalArgumentException();
		if (bitmap.length != REORDERING_WINDOW_SIZE / 8)
			throw new IllegalArgumentException();
		this.centre = centre;
		unseen = new HashSet<Long>();
		long bitmapBottom = centre - REORDERING_WINDOW_SIZE / 2;
		for (int bytes = 0; bytes < bitmap.length; bytes++) {
			for (int bits = 0; bits < 8; bits++) {
				long streamNumber = bitmapBottom + bytes * 8 + bits;
				if (streamNumber >= 0 && streamNumber <= MAX_32_BIT_UNSIGNED) {
					if ((bitmap[bytes] & (128 >> bits)) == 0)
						unseen.add(streamNumber);
				}
			}
		}
	}

	boolean isSeen(long streamNumber) {
		return !unseen.contains(streamNumber);
	}

	Collection<Long> setSeen(long streamNumber) {
		long bottom = getBottom(centre);
		long top = getTop(centre);
		if (streamNumber < bottom || streamNumber > top)
			throw new IllegalArgumentException();
		if (!unseen.remove(streamNumber))
			throw new IllegalArgumentException();
		Collection<Long> changed = new ArrayList<Long>();
		if (streamNumber >= centre) {
			centre = streamNumber + 1;
			long newBottom = getBottom(centre);
			long newTop = getTop(centre);
			for (long l = bottom; l < newBottom; l++) {
				if (unseen.remove(l)) changed.add(l);
			}
			for (long l = top + 1; l <= newTop; l++) {
				if (unseen.add(l)) changed.add(l);
			}
		}
		return changed;
	}

	long getCentre() {
		return centre;
	}

	byte[] getBitmap() {
		byte[] bitmap = new byte[REORDERING_WINDOW_SIZE / 8];
		long bitmapBottom = centre - REORDERING_WINDOW_SIZE / 2;
		for (int bytes = 0; bytes < bitmap.length; bytes++) {
			for (int bits = 0; bits < 8; bits++) {
				long streamNumber = bitmapBottom + bytes * 8 + bits;
				if (streamNumber >= 0 && streamNumber <= MAX_32_BIT_UNSIGNED) {
					if (!unseen.contains(streamNumber))
						bitmap[bytes] |= 128 >> bits;
				}
			}
		}
		return bitmap;
	}

	// Returns the lowest value contained in a window with the given centre
	private static long getBottom(long centre) {
		return Math.max(0, centre - REORDERING_WINDOW_SIZE / 2);
	}

	// Returns the highest value contained in a window with the given centre
	private static long getTop(long centre) {
		return Math.min(MAX_32_BIT_UNSIGNED,
				centre + REORDERING_WINDOW_SIZE / 2 - 1);
	}

	public Collection<Long> getUnseen() {
		return unseen;
	}
}
