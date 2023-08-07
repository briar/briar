package org.briarproject.bramble.api.data;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.FormatException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import static org.briarproject.bramble.api.data.BdfDictionary.NULL_VALUE;

/**
 * A BDF list contains zero or more BDF objects, which may be primitive types
 * (null, boolean, integer, float, string, raw) or nested containers (list,
 * dictionary).
 * <p>
 * Note that a BDF integer has the same range as a Java long, while a BDF
 * float has the same range as a Java double. Method names in this class
 * correspond to the Java types.
 * <p>
 * The getX() methods throw {@link FormatException} if the object at the
 * specified index is null or does not have the requested type.
 * <p>
 * The getOptionalX() methods return null if the object at the specified
 * index is null, or throw {@link FormatException} if the object does not
 * have the requested type.
 * <p>
 * The getX() methods that take a default value return the default value if
 * the object at the specified index is null, or throw
 * {@link FormatException} if the object does not have the requested type.
 * <p>
 * All of the getters throw {@link FormatException} if the specified index is
 * out of range.
 */
@NotThreadSafe
public final class BdfList extends ArrayList<Object> {

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

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private boolean isInRange(int index) {
		return index >= 0 && index < size();
	}

	public Boolean getBoolean(int index) throws FormatException {
		Boolean value = getOptionalBoolean(index);
		if (value == null) throw new FormatException();
		return value;
	}

	@Nullable
	public Boolean getOptionalBoolean(int index) throws FormatException {
		if (!isInRange(index)) throw new FormatException();
		Object o = get(index);
		if (o == null || o == NULL_VALUE) return null;
		if (o instanceof Boolean) return (Boolean) o;
		throw new FormatException();
	}

	public Boolean getBoolean(int index, Boolean defaultValue)
			throws FormatException {
		Boolean value = getOptionalBoolean(index);
		return value == null ? defaultValue : value;
	}

	public Long getLong(int index) throws FormatException {
		Long value = getOptionalLong(index);
		if (value == null) throw new FormatException();
		return value;
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

	public Long getLong(int index, Long defaultValue) throws FormatException {
		Long value = getOptionalLong(index);
		return value == null ? defaultValue : value;
	}

	/**
	 * Returns the integer at the specified index.
	 * <p>
	 * This method should be used in preference to
	 * <code>getLong(index).intValue()</code> as it checks for
	 * overflow/underflow.
	 *
	 * @throws FormatException if the index is out of range, or if the
	 * value at the specified index is null or cannot be represented as a
	 * Java int.
	 */
	public Integer getInt(int index) throws FormatException {
		Integer value = getOptionalInt(index);
		if (value == null) throw new FormatException();
		return value;
	}

	/**
	 * Returns the integer at the specified index, or null if the object at
	 * the specified index is null.
	 * <p>
	 * This method should be used in preference to
	 * <code>getOptionalLong(index).intValue()</code> as it checks for
	 * overflow/underflow.
	 *
	 * @throws FormatException if the index is out of range, or if the value
	 * at the specified index cannot be represented as a Java int.
	 */
	@Nullable
	public Integer getOptionalInt(int index) throws FormatException {
		Long value = getOptionalLong(index);
		if (value == null) return null;
		if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
			throw new FormatException();
		}
		return value.intValue();
	}

	/**
	 * Returns the integer at the specified index, or the given default value
	 * if the object at the specified index is null.
	 * <p>
	 * This method should be used in preference to
	 * <code>getLong(index, defaultValue).intValue()</code> as it checks for
	 * overflow/underflow.
	 *
	 * @throws FormatException if the index is out of range, or if the value
	 * at the specified index cannot be represented as a Java int.
	 */
	public Integer getInt(int index, Integer defaultValue)
			throws FormatException {
		Integer value = getOptionalInt(index);
		return value == null ? defaultValue : value;
	}

	public Double getDouble(int index) throws FormatException {
		Double value = getOptionalDouble(index);
		if (value == null) throw new FormatException();
		return value;
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

	public Double getDouble(int index, Double defaultValue)
			throws FormatException {
		Double value = getOptionalDouble(index);
		return value == null ? defaultValue : value;
	}

	public String getString(int index) throws FormatException {
		String value = getOptionalString(index);
		if (value == null) throw new FormatException();
		return value;
	}

	@Nullable
	public String getOptionalString(int index) throws FormatException {
		if (!isInRange(index)) throw new FormatException();
		Object o = get(index);
		if (o == null || o == NULL_VALUE) return null;
		if (o instanceof String) return (String) o;
		throw new FormatException();
	}

	public String getString(int index, String defaultValue)
			throws FormatException {
		String value = getOptionalString(index);
		return value == null ? defaultValue : value;
	}

	public byte[] getRaw(int index) throws FormatException {
		byte[] value = getOptionalRaw(index);
		if (value == null) throw new FormatException();
		return value;
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

	public byte[] getRaw(int index, byte[] defaultValue)
			throws FormatException {
		byte[] value = getOptionalRaw(index);
		return value == null ? defaultValue : value;
	}

	public BdfList getList(int index) throws FormatException {
		BdfList value = getOptionalList(index);
		if (value == null) throw new FormatException();
		return value;
	}

	@Nullable
	public BdfList getOptionalList(int index) throws FormatException {
		if (!isInRange(index)) throw new FormatException();
		Object o = get(index);
		if (o == null || o == NULL_VALUE) return null;
		if (o instanceof BdfList) return (BdfList) o;
		throw new FormatException();
	}

	public BdfList getList(int index, BdfList defaultValue)
			throws FormatException {
		BdfList value = getOptionalList(index);
		return value == null ? defaultValue : value;
	}

	public BdfDictionary getDictionary(int index) throws FormatException {
		BdfDictionary value = getOptionalDictionary(index);
		if (value == null) throw new FormatException();
		return value;
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

	public BdfDictionary getDictionary(int index, BdfDictionary defaultValue)
			throws FormatException {
		BdfDictionary value = getOptionalDictionary(index);
		return value == null ? defaultValue : value;
	}
}
