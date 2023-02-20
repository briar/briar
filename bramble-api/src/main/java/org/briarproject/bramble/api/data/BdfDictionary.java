package org.briarproject.bramble.api.data;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.FormatException;

import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
public final class BdfDictionary extends TreeMap<String, Object> {

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
	public static BdfDictionary of(Entry<String, ?>... entries) {
		BdfDictionary d = new BdfDictionary();
		for (Entry<String, ?> e : entries) d.put(e.getKey(), e.getValue());
		return d;
	}

	public BdfDictionary() {
		super();
	}

	public BdfDictionary(Map<String, ?> m) {
		super(m);
	}

	public Boolean getBoolean(String key) throws FormatException {
		Boolean value = getOptionalBoolean(key);
		if (value == null) throw new FormatException();
		return value;
	}

	@Nullable
	public Boolean getOptionalBoolean(String key) throws FormatException {
		Object o = get(key);
		if (o == null || o == NULL_VALUE) return null;
		if (o instanceof Boolean) return (Boolean) o;
		throw new FormatException();
	}

	public Boolean getBoolean(String key, Boolean defaultValue)
			throws FormatException {
		Boolean value = getOptionalBoolean(key);
		return value == null ? defaultValue : value;
	}

	public Long getLong(String key) throws FormatException {
		Long value = getOptionalLong(key);
		if (value == null) throw new FormatException();
		return value;
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

	public Long getLong(String key, Long defaultValue) throws FormatException {
		Long value = getOptionalLong(key);
		return value == null ? defaultValue : value;
	}

	public Integer getInt(String key) throws FormatException {
		Integer value = getOptionalInt(key);
		if (value == null) throw new FormatException();
		return value;
	}

	@Nullable
	public Integer getOptionalInt(String key) throws FormatException {
		Long value = getOptionalLong(key);
		if (value == null) return null;
		if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
			throw new FormatException();
		}
		return value.intValue();
	}

	public Integer getInt(String key, Integer defaultValue)
			throws FormatException {
		Integer value = getOptionalInt(key);
		return value == null ? defaultValue : value;
	}

	public Double getDouble(String key) throws FormatException {
		Double value = getOptionalDouble(key);
		if (value == null) throw new FormatException();
		return value;
	}

	@Nullable
	public Double getOptionalDouble(String key) throws FormatException {
		Object o = get(key);
		if (o == null || o == NULL_VALUE) return null;
		if (o instanceof Double) return (Double) o;
		if (o instanceof Float) return ((Float) o).doubleValue();
		throw new FormatException();
	}

	public Double getDouble(String key, Double defaultValue)
			throws FormatException {
		Double value = getOptionalDouble(key);
		return value == null ? defaultValue : value;
	}

	public String getString(String key) throws FormatException {
		String value = getOptionalString(key);
		if (value == null) throw new FormatException();
		return value;
	}

	@Nullable
	public String getOptionalString(String key) throws FormatException {
		Object o = get(key);
		if (o == null || o == NULL_VALUE) return null;
		if (o instanceof String) return (String) o;
		throw new FormatException();
	}

	public String getString(String key, String defaultValue)
			throws FormatException {
		String value = getOptionalString(key);
		return value == null ? defaultValue : value;
	}

	public byte[] getRaw(String key) throws FormatException {
		byte[] value = getOptionalRaw(key);
		if (value == null) throw new FormatException();
		return value;
	}

	@Nullable
	public byte[] getOptionalRaw(String key) throws FormatException {
		Object o = get(key);
		if (o == null || o == NULL_VALUE) return null;
		if (o instanceof byte[]) return (byte[]) o;
		if (o instanceof Bytes) return ((Bytes) o).getBytes();
		throw new FormatException();
	}

	public byte[] getRaw(String key, byte[] defaultValue)
			throws FormatException {
		byte[] value = getOptionalRaw(key);
		return value == null ? defaultValue : value;
	}

	public BdfList getList(String key) throws FormatException {
		BdfList value = getOptionalList(key);
		if (value == null) throw new FormatException();
		return value;
	}

	@Nullable
	public BdfList getOptionalList(String key) throws FormatException {
		Object o = get(key);
		if (o == null || o == NULL_VALUE) return null;
		if (o instanceof BdfList) return (BdfList) o;
		throw new FormatException();
	}

	public BdfList getList(String key, BdfList defaultValue)
			throws FormatException {
		BdfList value = getOptionalList(key);
		return value == null ? defaultValue : value;
	}

	public BdfDictionary getDictionary(String key) throws FormatException {
		BdfDictionary value = getOptionalDictionary(key);
		if (value == null) throw new FormatException();
		return value;
	}

	@Nullable
	public BdfDictionary getOptionalDictionary(String key)
			throws FormatException {
		Object o = get(key);
		if (o == null || o == NULL_VALUE) return null;
		if (o instanceof BdfDictionary) return (BdfDictionary) o;
		throw new FormatException();
	}

	public BdfDictionary getDictionary(String key, BdfDictionary defaultValue)
			throws FormatException {
		BdfDictionary value = getOptionalDictionary(key);
		return value == null ? defaultValue : value;
	}
}
