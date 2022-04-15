package org.briarproject.bramble.api;

import org.briarproject.bramble.util.StringUtils;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public abstract class StringMap extends Hashtable<String, String> {

	protected StringMap(Map<String, String> m) {
		super(m);
	}

	protected StringMap() {
		super();
	}

	public boolean getBoolean(String key, boolean defaultValue) {
		String s = get(key);
		if (s == null) return defaultValue;
		if ("true".equals(s)) return true;
		if ("false".equals(s)) return false;
		return defaultValue;
	}

	public void putBoolean(String key, boolean value) {
		put(key, String.valueOf(value));
	}

	public int getInt(String key, int defaultValue) {
		String s = get(key);
		if (s == null) return defaultValue;
		try {
			return Integer.valueOf(s);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	public void putInt(String key, int value) {
		put(key, String.valueOf(value));
	}

	public long getLong(String key, long defaultValue) {
		String s = get(key);
		if (s == null) return defaultValue;
		try {
			return Long.valueOf(s);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	public void putLong(String key, long value) {
		put(key, String.valueOf(value));
	}

	@Nullable
	public int[] getIntArray(String key) {
		String s = get(key);
		if (s == null) return null;
		// Handle empty string because "".split(",") returns {""}
		if (s.length() == 0) return new int[0];
		String[] intStrings = s.split(",");
		int[] ints = new int[intStrings.length];
		try {
			for (int i = 0; i < ints.length; i++) {
				ints[i] = Integer.parseInt(intStrings[i]);
			}
		} catch (NumberFormatException e) {
			return null;
		}
		return ints;
	}

	public void putIntArray(String key, int[] value) {
		List<String> intStrings = new ArrayList<>();
		for (int integer : value) {
			intStrings.add(String.valueOf(integer));
		}
		// Puts empty string if input array value is empty
		put(key, StringUtils.join(intStrings, ","));
	}
}
