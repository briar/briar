package org.briarproject.api.data;

import java.util.Hashtable;

public class BdfDictionary extends Hashtable<String, Object> {

	public Boolean getBoolean(String key, Boolean defaultValue) {
		Object o = get(key);
		if (o instanceof Boolean) return (Boolean) o;
		return defaultValue;
	}

	public Long getInteger(String key, Long defaultValue) {
		Object o = get(key);
		if (o instanceof Long) return (Long) o;
		return defaultValue;
	}

	public Double getFloat(String key, Double defaultValue) {
		Object o = get(key);
		if (o instanceof Double) return (Double) o;
		return defaultValue;
	}

	public String getString(String key, String defaultValue) {
		Object o = get(key);
		if (o instanceof String) return (String) o;
		return defaultValue;
	}

	public byte[] getRaw(String key, byte[] defaultValue) {
		Object o = get(key);
		if (o instanceof byte[]) return (byte[]) o;
		return defaultValue;
	}

	public BdfList getList(String key, BdfList defaultValue) {
		Object o = get(key);
		if (o instanceof BdfList) return (BdfList) o;
		return defaultValue;
	}

	public BdfDictionary getDictionary(String key, BdfDictionary defaultValue) {
		Object o = get(key);
		if (o instanceof BdfDictionary) return (BdfDictionary) o;
		return defaultValue;
	}
}
