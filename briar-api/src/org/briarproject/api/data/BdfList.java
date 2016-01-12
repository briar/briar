package org.briarproject.api.data;

import java.util.ArrayList;

// This class is not thread-safe
public class BdfList extends ArrayList<Object> {

	public Boolean getBoolean(int index, Boolean defaultValue) {
		Object o = get(index);
		if (o instanceof Boolean) return (Boolean) o;
		return defaultValue;
	}

	public Long getInteger(int index, Long defaultValue) {
		Object o = get(index);
		if (o instanceof Long) return (Long) o;
		return defaultValue;
	}

	public Double getFloat(int index, Double defaultValue) {
		Object o = get(index);
		if (o instanceof Double) return (Double) o;
		return defaultValue;
	}

	public String getString(int index, String defaultValue) {
		Object o = get(index);
		if (o instanceof String) return (String) o;
		return defaultValue;
	}

	public byte[] getRaw(int index, byte[] defaultValue) {
		Object o = get(index);
		if (o instanceof byte[]) return (byte[]) o;
		return defaultValue;
	}

	public BdfList getList(int index, BdfList defaultValue) {
		Object o = get(index);
		if (o instanceof BdfList) return (BdfList) o;
		return defaultValue;
	}

	public BdfDictionary getDictionary(int index, BdfDictionary defaultValue) {
		Object o = get(index);
		if (o instanceof BdfDictionary) return (BdfDictionary) o;
		return defaultValue;
	}
}
