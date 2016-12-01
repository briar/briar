package org.briarproject.bramble.api;

import java.util.Hashtable;
import java.util.Map;

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
}
