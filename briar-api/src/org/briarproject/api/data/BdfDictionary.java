package org.briarproject.api.data;

import org.briarproject.api.Bytes;
import org.briarproject.api.FormatException;

import java.util.Hashtable;

public class BdfDictionary extends Hashtable<String, Object> {

	public static final Object NULL_VALUE = new Object();

	public Boolean getBoolean(String key) throws FormatException {
		Object o = get(key);
		if (o instanceof Boolean) return (Boolean) o;
		throw new FormatException();
	}

	public Boolean getBoolean(String key, Boolean defaultValue) {
		Object o = get(key);
		if (o instanceof Boolean) return (Boolean) o;
		return defaultValue;
	}

	public Long getInteger(String key) throws FormatException {
		Object o = get(key);
		if (o instanceof Long) return (Long) o;
		if (o instanceof Integer) return ((Integer) o).longValue();
		if (o instanceof Short) return ((Short) o).longValue();
		if (o instanceof Byte) return ((Byte) o).longValue();
		throw new FormatException();
	}

	public Long getInteger(String key, Long defaultValue) {
		Object o = get(key);
		if (o instanceof Long) return (Long) o;
		if (o instanceof Integer) return ((Integer) o).longValue();
		if (o instanceof Short) return ((Short) o).longValue();
		if (o instanceof Byte) return ((Byte) o).longValue();
		return defaultValue;
	}

	public Double getFloat(String key) throws FormatException {
		Object o = get(key);
		if (o instanceof Double) return (Double) o;
		if (o instanceof Float) return ((Float) o).doubleValue();
		throw new FormatException();
	}

	public Double getFloat(String key, Double defaultValue) {
		Object o = get(key);
		if (o instanceof Double) return (Double) o;
		if (o instanceof Float) return ((Float) o).doubleValue();
		return defaultValue;
	}

	public String getString(String key) throws FormatException {
		Object o = get(key);
		if (o instanceof String) return (String) o;
		throw new FormatException();
	}

	public String getString(String key, String defaultValue) {
		Object o = get(key);
		if (o instanceof String) return (String) o;
		return defaultValue;
	}

	public byte[] getRaw(String key) throws FormatException {
		Object o = get(key);
		if (o instanceof byte[]) return (byte[]) o;
		if (o instanceof Bytes) return ((Bytes) o).getBytes();
		throw new FormatException();
	}

	public byte[] getRaw(String key, byte[] defaultValue) {
		Object o = get(key);
		if (o instanceof byte[]) return (byte[]) o;
		if (o instanceof Bytes) return ((Bytes) o).getBytes();
		return defaultValue;
	}

	public BdfList getList(String key) throws FormatException {
		Object o = get(key);
		if (o instanceof BdfList) return (BdfList) o;
		throw new FormatException();
	}

	public BdfList getList(String key, BdfList defaultValue) {
		Object o = get(key);
		if (o instanceof BdfList) return (BdfList) o;
		return defaultValue;
	}

	public BdfDictionary getDictionary(String key) throws FormatException {
		Object o = get(key);
		if (o instanceof BdfDictionary) return (BdfDictionary) o;
		throw new FormatException();
	}

	public BdfDictionary getDictionary(String key, BdfDictionary defaultValue) {
		Object o = get(key);
		if (o instanceof BdfDictionary) return (BdfDictionary) o;
		return defaultValue;
	}
}
