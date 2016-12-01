package org.briarproject.bramble.api.data;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.FormatException;

import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import javax.annotation.Nullable;

public class BdfDictionary extends ConcurrentSkipListMap<String, Object> {

	public static final Object NULL_VALUE = new Object();

	/**
	 * Factory method for constructing dictionaries inline.
	 * <pre>
	 * BdfDictionary.of(
	 *     new BdfEntry("foo", foo),
	 *     new BdfEntry("bar", bar)
	 * );
	 * </pre>
	 */
	public static BdfDictionary of(Entry<String, Object>... entries) {
		BdfDictionary d = new BdfDictionary();
		for (Entry<String, Object> e : entries) d.put(e.getKey(), e.getValue());
		return d;
	}

	public BdfDictionary() {
		super();
	}

	public BdfDictionary(Map<String, Object> m) {
		super(m);
	}

	public Boolean getBoolean(String key) throws FormatException {
		Object o = get(key);
		if (o instanceof Boolean) return (Boolean) o;
		throw new FormatException();
	}

	@Nullable
	public Boolean getOptionalBoolean(String key) throws FormatException {
		Object o = get(key);
		if (o == null || o == NULL_VALUE) return null;
		if (o instanceof Boolean) return (Boolean) o;
		throw new FormatException();
	}

	public Boolean getBoolean(String key, Boolean defaultValue) {
		Object o = get(key);
		if (o instanceof Boolean) return (Boolean) o;
		return defaultValue;
	}

	public Long getLong(String key) throws FormatException {
		Object o = get(key);
		if (o instanceof Long) return (Long) o;
		if (o instanceof Integer) return ((Integer) o).longValue();
		if (o instanceof Short) return ((Short) o).longValue();
		if (o instanceof Byte) return ((Byte) o).longValue();
		throw new FormatException();
	}

	@Nullable
	public Long getOptionalLong(String key) throws FormatException {
		Object o = get(key);
		if (o == null || o == NULL_VALUE) return null;
		if (o instanceof Long) return (Long) o;
		if (o instanceof Integer) return ((Integer) o).longValue();
		if (o instanceof Short) return ((Short) o).longValue();
		if (o instanceof Byte) return ((Byte) o).longValue();
		throw new FormatException();
	}

	public Long getLong(String key, Long defaultValue) {
		Object o = get(key);
		if (o instanceof Long) return (Long) o;
		if (o instanceof Integer) return ((Integer) o).longValue();
		if (o instanceof Short) return ((Short) o).longValue();
		if (o instanceof Byte) return ((Byte) o).longValue();
		return defaultValue;
	}

	public Double getDouble(String key) throws FormatException {
		Object o = get(key);
		if (o instanceof Double) return (Double) o;
		if (o instanceof Float) return ((Float) o).doubleValue();
		throw new FormatException();
	}

	@Nullable
	public Double getOptionalDouble(String key) throws FormatException {
		Object o = get(key);
		if (o == null || o == NULL_VALUE) return null;
		if (o instanceof Double) return (Double) o;
		if (o instanceof Float) return ((Float) o).doubleValue();
		throw new FormatException();
	}

	public Double getDouble(String key, Double defaultValue) {
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

	@Nullable
	public String getOptionalString(String key) throws FormatException {
		Object o = get(key);
		if (o == null || o == NULL_VALUE) return null;
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

	@Nullable
	public byte[] getOptionalRaw(String key) throws FormatException {
		Object o = get(key);
		if (o == null || o == NULL_VALUE) return null;
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

	@Nullable
	public BdfList getOptionalList(String key) throws FormatException {
		Object o = get(key);
		if (o == null || o == NULL_VALUE) return null;
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

	@Nullable
	public BdfDictionary getOptionalDictionary(String key)
			throws FormatException {
		Object o = get(key);
		if (o == null || o == NULL_VALUE) return null;
		if (o instanceof BdfDictionary) return (BdfDictionary) o;
		throw new FormatException();
	}

	public BdfDictionary getDictionary(String key, BdfDictionary defaultValue) {
		Object o = get(key);
		if (o instanceof BdfDictionary) return (BdfDictionary) o;
		return defaultValue;
	}
}
