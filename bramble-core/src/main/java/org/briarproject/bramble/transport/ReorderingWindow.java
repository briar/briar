package org.briarproject.bramble.transport;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.NotThreadSafe;

import static org.briarproject.bramble.util.ByteUtils.MAX_32_BIT_UNSIGNED;

@NotThreadSafe
@NotNullByDefault
class ReorderingWindow {

	private long base;
	private boolean[] seen;

	ReorderingWindow(long base, byte[] bitmap) {
		if (base < 0) throw new IllegalArgumentException();
		if (base > MAX_32_BIT_UNSIGNED + 1)
			throw new IllegalArgumentException();
		this.base = base;
		seen = new boolean[bitmap.length * 8];
		for (int i = 0; i < bitmap.length; i++) {
			for (int j = 0; j < 8; j++) {
				if ((bitmap[i] & (128 >> j)) != 0) seen[i * 8 + j] = true;
			}
		}
	}

	long getBase() {
		return base;
	}

	byte[] getBitmap() {
		byte[] bitmap = new byte[seen.length / 8];
		for (int i = 0; i < bitmap.length; i++) {
			for (int j = 0; j < 8; j++) {
				if (seen[i * 8 + j]) bitmap[i] |= 128 >> j;
			}
		}
		return bitmap;
	}

	List<Long> getUnseen() {
		List<Long> unseen = new ArrayList<Long>(seen.length);
		for (int i = 0; i < seen.length; i++)
			if (!seen[i]) unseen.add(base + i);
		return unseen;
	}

	Change setSeen(long index) {
		if (index < base) throw new IllegalArgumentException();
		if (index >= base + seen.length) throw new IllegalArgumentException();
		if (index > MAX_32_BIT_UNSIGNED) throw new IllegalArgumentException();
		int offset = (int) (index - base);
		if (seen[offset]) throw new IllegalArgumentException();
		seen[offset] = true;
		// Rule 1: Slide until all elements above the midpoint are unseen
		int slide = Math.max(0, offset + 1 - seen.length / 2);
		// Rule 2: Slide until the lowest element is unseen
		while (seen[slide]) slide++;
		// If the window doesn't need to slide, return
		if (slide == 0) {
			List<Long> added = Collections.emptyList();
			List<Long> removed = Collections.singletonList(index);
			return new Change(added, removed);
		}
		// Record the elements that will be added and removed
		List<Long> added = new ArrayList<Long>(slide);
		List<Long> removed = new ArrayList<Long>(slide);
		for (int i = 0; i < slide; i++) {
			if (!seen[i]) removed.add(base + i);
			added.add(base + seen.length + i);
		}
		removed.add(index);
		// Update the window
		base += slide;
		for (int i = 0; i + slide < seen.length; i++) seen[i] = seen[i + slide];
		for (int i = seen.length - slide; i < seen.length; i++) seen[i] = false;
		return new Change(added, removed);
	}

	static class Change {

		private final List<Long> added, removed;

		Change(List<Long> added, List<Long> removed) {
			this.added = added;
			this.removed = removed;
		}

		List<Long> getAdded() {
			return added;
		}

		List<Long> getRemoved() {
			return removed;
		}
	}
}
