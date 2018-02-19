package org.briarproject.bramble.api;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
public class Multiset<T> {

	private final Map<T, Integer> map = new HashMap<>();

	private int total = 0;

	/**
	 * Returns how many items the multiset contains in total.
	 */
	public int getTotal() {
		return total;
	}

	/**
	 * Returns how many unique items the multiset contains.
	 */
	public int getUnique() {
		return map.size();
	}

	/**
	 * Returns how many of the given item the multiset contains.
	 */
	public int getCount(T t) {
		Integer count = map.get(t);
		return count == null ? 0 : count;
	}

	/**
	 * Adds the given item to the multiset and returns how many of the item
	 * the multiset now contains.
	 */
	public int add(T t) {
		Integer count = map.get(t);
		if (count == null) count = 0;
		map.put(t, count + 1);
		total++;
		return count + 1;
	}

	/**
	 * Removes the given item from the multiset and returns how many of the
	 * item the multiset now contains.
	 * @throws NoSuchElementException if the item is not in the multiset.
	 */
	public int remove(T t) {
		Integer count = map.get(t);
		if (count == null) throw new NoSuchElementException();
		if (count == 1) map.remove(t);
		else map.put(t, count - 1);
		total--;
		return count - 1;
	}

	/**
	 * Removes all occurrences of the given item from the multiset.
	 */
	public int removeAll(T t) {
		Integer count = map.remove(t);
		if (count == null) return 0;
		total -= count;
		return count;
	}

	/**
	 * Returns true if the multiset contains any occurrences of the given item.
	 */
	public boolean contains(T t) {
		return map.containsKey(t);
	}

	/**
	 * Removes all items from the multiset.
	 */
	public void clear() {
		map.clear();
		total = 0;
	}

	/**
	 * Returns the set of unique items the multiset contains. The returned set
	 * is unmodifiable.
	 */
	public Set<T> keySet() {
		return Collections.unmodifiableSet(map.keySet());
	}
}
