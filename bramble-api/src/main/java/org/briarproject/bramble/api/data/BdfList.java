package org.briarproject.bramble.api.data;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.FormatException;

import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import javax.annotation.Nullable;

import static org.briarproject.bramble.api.data.BdfDictionary.NULL_VALUE;

public class BdfList extends Vector<Object> {

	/**
	 * Factory method for constructing lists inline.
	 * <pre>
	 * BdfList.of(1, 2, 3);
	 * </pre>
	 */
	public static BdfList of(Object... items) {
		return new BdfList(Arrays.asList(items));
	}

	public BdfList() {
		super();
	}

	public BdfList(List<Object> items) {
		super(items);
	}

	private boolean isInRange(int index) {
		return index >= 0 && index < size();
	}

	public Boolean getBoolean(int index) throws FormatException {
		if (!isInRange(index)) throw new FormatException();
		Object o = get(index);
		if (o instanceof Boolean) return (Boolean) o;
		throw new FormatException();
	}

	@Nullable
	public Boolean getOptionalBoolean(int index) throws FormatException {
		if (!isInRange(index)) throw new FormatException();
		Object o = get(index);
		if (o == null || o == NULL_VALUE) return null;
		if (o instanceof Boolean) return (Boolean) o;
		throw new FormatException();
	}

	public Boolean getBoolean(int index, Boolean defaultValue) {
		if (!isInRange(index)) return defaultValue;
		Object o = get(index);
		if (o instanceof Boolean) return (Boolean) o;
		return defaultValue;
	}

	public Long getLong(int index) throws FormatException {
		if (!isInRange(index)) throw new FormatException();
		Object o = get(index);
		if (o instanceof Long) return (Long) o;
		if (o instanceof Integer) return ((Integer) o).longValue();
		if (o instanceof Short) return ((Short) o).longValue();
		if (o instanceof Byte) return ((Byte) o).longValue();
		throw new FormatException();
	}

	@Nullable
	public Long getOptionalLong(int index) throws FormatException {
		if (!isInRange(index)) throw new FormatException();
		Object o = get(index);
		if (o == null || o == NULL_VALUE) return null;
		if (o instanceof Long) return (Long) o;
		if (o instanceof Integer) return ((Integer) o).longValue();
		if (o instanceof Short) return ((Short) o).longValue();
		if (o instanceof Byte) return ((Byte) o).longValue();
		throw new FormatException();
	}

	public Long getLong(int index, Long defaultValue) {
		if (!isInRange(index)) return defaultValue;
		Object o = get(index);
		if (o instanceof Long) return (Long) o;
		if (o instanceof Integer) return ((Integer) o).longValue();
		if (o instanceof Short) return ((Short) o).longValue();
		if (o instanceof Byte) return ((Byte) o).longValue();
		return defaultValue;
	}

	public Double getDouble(int index) throws FormatException {
		if (!isInRange(index)) throw new FormatException();
		Object o = get(index);
		if (o instanceof Double) return (Double) o;
		if (o instanceof Float) return ((Float) o).doubleValue();
		throw new FormatException();
	}

	@Nullable
	public Double getOptionalDouble(int index) throws FormatException {
		if (!isInRange(index)) throw new FormatException();
		Object o = get(index);
		if (o == null || o == NULL_VALUE) return null;
		if (o instanceof Double) return (Double) o;
		if (o instanceof Float) return ((Float) o).doubleValue();
		throw new FormatException();
	}

	public Double getDouble(int index, Double defaultValue) {
		if (!isInRange(index)) return defaultValue;
		Object o = get(index);
		if (o instanceof Double) return (Double) o;
		if (o instanceof Float) return ((Float) o).doubleValue();
		return defaultValue;
	}

	public String getString(int index) throws FormatException {
		if (!isInRange(index)) throw new FormatException();
		Object o = get(index);
		if (o instanceof String) return (String) o;
		throw new FormatException();
	}

	@Nullable
	public String getOptionalString(int index) throws FormatException {
		if (!isInRange(index)) throw new FormatException();
		Object o = get(index);
		if (o == null || o == NULL_VALUE) return null;
		if (o instanceof String) return (String) o;
		throw new FormatException();
	}

	public String getString(int index, String defaultValue) {
		if (!isInRange(index)) return defaultValue;
		Object o = get(index);
		if (o instanceof String) return (String) o;
		return defaultValue;
	}

	public byte[] getRaw(int index) throws FormatException {
		if (!isInRange(index)) throw new FormatException();
		Object o = get(index);
		if (o instanceof byte[]) return (byte[]) o;
		if (o instanceof Bytes) return ((Bytes) o).getBytes();
		throw new FormatException();
	}

	@Nullable
	public byte[] getOptionalRaw(int index) throws FormatException {
		if (!isInRange(index)) throw new FormatException();
		Object o = get(index);
		if (o == null || o == NULL_VALUE) return null;
		if (o instanceof byte[]) return (byte[]) o;
		if (o instanceof Bytes) return ((Bytes) o).getBytes();
		throw new FormatException();
	}

	public byte[] getRaw(int index, byte[] defaultValue) {
		if (!isInRange(index)) return defaultValue;
		Object o = get(index);
		if (o instanceof byte[]) return (byte[]) o;
		if (o instanceof Bytes) return ((Bytes) o).getBytes();
		return defaultValue;
	}

	public BdfList getList(int index) throws FormatException {
		if (!isInRange(index)) throw new FormatException();
		Object o = get(index);
		if (o instanceof BdfList) return (BdfList) o;
		throw new FormatException();
	}

	@Nullable
	public BdfList getOptionalList(int index) throws FormatException {
		if (!isInRange(index)) throw new FormatException();
		Object o = get(index);
		if (o == null || o == NULL_VALUE) return null;
		if (o instanceof BdfList) return (BdfList) o;
		throw new FormatException();
	}

	public BdfList getList(int index, BdfList defaultValue) {
		if (!isInRange(index)) return defaultValue;
		Object o = get(index);
		if (o instanceof BdfList) return (BdfList) o;
		return defaultValue;
	}

	public BdfDictionary getDictionary(int index) throws FormatException {
		if (!isInRange(index)) throw new FormatException();
		Object o = get(index);
		if (o instanceof BdfDictionary) return (BdfDictionary) o;
		throw new FormatException();
	}

	@Nullable
	public BdfDictionary getOptionalDictionary(int index)
			throws FormatException {
		if (!isInRange(index)) throw new FormatException();
		Object o = get(index);
		if (o == null || o == NULL_VALUE) return null;
		if (o instanceof BdfDictionary) return (BdfDictionary) o;
		throw new FormatException();
	}

	public BdfDictionary getDictionary(int index, BdfDictionary defaultValue) {
		if (!isInRange(index)) return defaultValue;
		Object o = get(index);
		if (o instanceof BdfDictionary) return (BdfDictionary) o;
		return defaultValue;
	}
}
