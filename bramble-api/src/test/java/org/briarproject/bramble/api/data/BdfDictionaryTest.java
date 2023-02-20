package org.briarproject.bramble.api.data;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;

import static java.lang.Boolean.TRUE;
import static java.util.Collections.singletonMap;
import static org.briarproject.bramble.api.data.BdfDictionary.NULL_VALUE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BdfDictionaryTest extends BrambleTestCase {

	@Test
	public void testConstructors() {
		assertEquals(Collections.<String, Object>emptyMap(),
				new BdfDictionary());
		assertEquals(singletonMap("foo", NULL_VALUE),
				new BdfDictionary(singletonMap("foo", NULL_VALUE)));
	}

	@Test
	public void testFactoryMethod() {
		assertEquals(Collections.<String, Object>emptyMap(),
				BdfDictionary.of());
		assertEquals(singletonMap("foo", NULL_VALUE),
				BdfDictionary.of(new BdfEntry("foo", NULL_VALUE)));
	}

	@Test
	public void testLongPromotion() throws Exception {
		BdfDictionary d = new BdfDictionary();
		d.put("foo", (byte) 1);
		d.put("bar", (short) 2);
		d.put("baz", 3);
		d.put("bam", 4L);
		assertEquals(Long.valueOf(1), d.getLong("foo"));
		assertEquals(Long.valueOf(2), d.getLong("bar"));
		assertEquals(Long.valueOf(3), d.getLong("baz"));
		assertEquals(Long.valueOf(4), d.getLong("bam"));
	}

	@Test
	public void testIntPromotionAndDemotion() throws Exception {
		BdfDictionary d = new BdfDictionary();
		d.put("foo", (byte) 1);
		d.put("bar", (short) 2);
		d.put("baz", 3);
		d.put("bam", 4L);
		assertEquals(Integer.valueOf(1), d.getInt("foo"));
		assertEquals(Integer.valueOf(2), d.getInt("bar"));
		assertEquals(Integer.valueOf(3), d.getInt("baz"));
		assertEquals(Integer.valueOf(4), d.getInt("bam"));
	}

	@Test(expected = FormatException.class)
	public void testIntUnderflow() throws Exception {
		BdfDictionary d =
				BdfDictionary.of(new BdfEntry("foo", Integer.MIN_VALUE - 1L));
		d.getInt("foo");
	}

	@Test(expected = FormatException.class)
	public void testIntOverflow() throws Exception {
		BdfDictionary d =
				BdfDictionary.of(new BdfEntry("foo", Integer.MAX_VALUE + 1L));
		d.getInt("foo");
	}

	@Test
	public void testFloatPromotion() throws Exception {
		BdfDictionary d = new BdfDictionary();
		d.put("foo", 1F);
		d.put("bar", 2D);
		assertEquals(Double.valueOf(1), d.getDouble("foo"));
		assertEquals(Double.valueOf(2), d.getDouble("bar"));
	}

	@Test
	public void testByteArrayUnwrapping() throws Exception {
		BdfDictionary d = new BdfDictionary();
		d.put("foo", new byte[123]);
		d.put("bar", new Bytes(new byte[123]));
		byte[] foo = d.getRaw("foo");
		assertEquals(123, foo.length);
		assertArrayEquals(new byte[123], foo);
		byte[] bar = d.getRaw("bar");
		assertEquals(123, bar.length);
		assertArrayEquals(new byte[123], bar);
	}

	@Test
	public void testKeySetIteratorIsOrderedByKeys() {
		BdfDictionary d = new BdfDictionary();
		d.put("a", 1);
		d.put("d", 4);
		d.put("b", 2);
		d.put("c", 3);
		// Keys should be returned in their natural order
		Iterator<String> it = d.keySet().iterator();
		assertTrue(it.hasNext());
		assertEquals("a", it.next());
		assertTrue(it.hasNext());
		assertEquals("b", it.next());
		assertTrue(it.hasNext());
		assertEquals("c", it.next());
		assertTrue(it.hasNext());
		assertEquals("d", it.next());
	}

	@Test
	public void testValuesIteratorIsOrderedByKeys() {
		BdfDictionary d = new BdfDictionary();
		d.put("a", 1);
		d.put("d", 4);
		d.put("b", 2);
		d.put("c", 3);
		// Values should be returned in the natural order of their keys
		Iterator<Object> it = d.values().iterator();
		assertTrue(it.hasNext());
		assertEquals(1, it.next());
		assertTrue(it.hasNext());
		assertEquals(2, it.next());
		assertTrue(it.hasNext());
		assertEquals(3, it.next());
		assertTrue(it.hasNext());
		assertEquals(4, it.next());
	}

	@Test
	public void testEntrySetIteratorIsOrderedByKeys() {
		BdfDictionary d = new BdfDictionary();
		d.put("a", 1);
		d.put("d", 4);
		d.put("b", 2);
		d.put("c", 3);
		// Entries should be returned in the natural order of their keys
		Iterator<Entry<String, Object>> it = d.entrySet().iterator();
		assertTrue(it.hasNext());
		Entry<String, Object> e = it.next();
		assertEquals("a", e.getKey());
		assertEquals(1, e.getValue());
		assertTrue(it.hasNext());
		e = it.next();
		assertEquals("b", e.getKey());
		assertEquals(2, e.getValue());
		assertTrue(it.hasNext());
		e = it.next();
		assertEquals("c", e.getKey());
		assertEquals(3, e.getValue());
		assertTrue(it.hasNext());
		e = it.next();
		assertEquals("d", e.getKey());
		assertEquals(4, e.getValue());
	}

	@Test(expected = FormatException.class)
	public void testMissingValueForBooleanThrowsFormatException()
			throws Exception {
		new BdfDictionary().getBoolean("foo");
	}

	@Test(expected = FormatException.class)
	public void testMissingValueForLongThrowsFormatException()
			throws Exception {
		new BdfDictionary().getLong("foo");
	}

	@Test(expected = FormatException.class)
	public void testMissingValueForIntThrowsFormatException() throws Exception {
		new BdfDictionary().getInt("foo");
	}

	@Test(expected = FormatException.class)
	public void testMissingValueForDoubleThrowsFormatException()
			throws Exception {
		new BdfDictionary().getDouble("foo");
	}

	@Test(expected = FormatException.class)
	public void testMissingValueForStringThrowsFormatException()
			throws Exception {
		new BdfDictionary().getString("foo");
	}

	@Test(expected = FormatException.class)
	public void testMissingValueForRawThrowsFormatException() throws Exception {
		new BdfDictionary().getRaw("foo");
	}

	@Test(expected = FormatException.class)
	public void testMissingValueForListThrowsFormatException()
			throws Exception {
		new BdfDictionary().getList("foo");
	}

	@Test(expected = FormatException.class)
	public void testMissingValueForDictionaryThrowsFormatException()
			throws Exception {
		new BdfDictionary().getDictionary("foo");
	}

	@Test(expected = FormatException.class)
	public void testNullValueForBooleanThrowsFormatException()
			throws Exception {
		BdfDictionary.of(new BdfEntry("foo", NULL_VALUE)).getBoolean("foo");
	}

	@Test(expected = FormatException.class)
	public void testNullValueForLongThrowsFormatException() throws Exception {
		BdfDictionary.of(new BdfEntry("foo", NULL_VALUE)).getLong("foo");
	}

	@Test(expected = FormatException.class)
	public void testNullValueForIntThrowsFormatException() throws Exception {
		BdfDictionary.of(new BdfEntry("foo", NULL_VALUE)).getInt("foo");
	}

	@Test(expected = FormatException.class)
	public void testNullValueForDoubleThrowsFormatException() throws Exception {
		BdfDictionary.of(new BdfEntry("foo", NULL_VALUE)).getDouble("foo");
	}

	@Test(expected = FormatException.class)
	public void testNullValueForStringThrowsFormatException() throws Exception {
		BdfDictionary.of(new BdfEntry("foo", NULL_VALUE)).getString("foo");
	}

	@Test(expected = FormatException.class)
	public void testNullValueForRawThrowsFormatException() throws Exception {
		BdfDictionary.of(new BdfEntry("foo", NULL_VALUE)).getRaw("foo");
	}

	@Test(expected = FormatException.class)
	public void testNullValueForListThrowsFormatException() throws Exception {
		BdfDictionary.of(new BdfEntry("foo", NULL_VALUE)).getList("foo");
	}

	@Test(expected = FormatException.class)
	public void testNullValueForDictionaryThrowsFormatException()
			throws Exception {
		BdfDictionary.of(new BdfEntry("foo", NULL_VALUE)).getDictionary("foo");
	}

	@Test
	public void testOptionalMethodsReturnNullForMissingValue()
			throws Exception {
		testOptionalMethodsReturnNull(new BdfDictionary());
	}

	@Test
	public void testOptionalMethodsReturnNullForNullValue() throws Exception {
		BdfDictionary d = BdfDictionary.of(new BdfEntry("foo", NULL_VALUE));
		testOptionalMethodsReturnNull(d);
	}

	private void testOptionalMethodsReturnNull(BdfDictionary d)
			throws Exception {
		assertNull(d.getOptionalBoolean("foo"));
		assertNull(d.getOptionalLong("foo"));
		assertNull(d.getOptionalInt("foo"));
		assertNull(d.getOptionalDouble("foo"));
		assertNull(d.getOptionalString("foo"));
		assertNull(d.getOptionalRaw("foo"));
		assertNull(d.getOptionalList("foo"));
		assertNull(d.getOptionalDictionary("foo"));
	}

	@Test
	public void testDefaultMethodsReturnDefaultForMissingValue()
			throws Exception {
		testDefaultMethodsReturnDefault(new BdfDictionary());
	}

	@Test
	public void testDefaultMethodsReturnDefaultForNullValue() throws Exception {
		BdfDictionary d = BdfDictionary.of(new BdfEntry("foo", NULL_VALUE));
		testDefaultMethodsReturnDefault(d);
	}

	private void testDefaultMethodsReturnDefault(BdfDictionary d)
			throws Exception {
		assertEquals(TRUE, d.getBoolean("foo", TRUE));
		assertEquals(Long.valueOf(123L), d.getLong("foo", 123L));
		assertEquals(Integer.valueOf(123), d.getInt("foo", 123));
		assertEquals(Double.valueOf(123D), d.getDouble("foo", 123D));
		assertEquals("123", d.getString("foo", "123"));
		byte[] defaultRaw = {1, 2, 3};
		assertArrayEquals(defaultRaw, d.getRaw("foo", defaultRaw));
		BdfList defaultList = BdfList.of(1, 2, 3);
		assertEquals(defaultList, d.getList("foo", defaultList));
		BdfDictionary defaultDict = BdfDictionary.of(new BdfEntry("123", 123));
		assertEquals(defaultDict, d.getDictionary("foo", defaultDict));
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForBooleanThrowsFormatException()
			throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 123)).getBoolean("foo");
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForOptionalBooleanThrowsFormatException()
			throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 123)).getOptionalBoolean("foo");
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForDefaultBooleanThrowsFormatException()
			throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 123)).getBoolean("foo", true);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForLongThrowsFormatException() throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 1.23)).getLong("foo");
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForOptionalLongThrowsFormatException()
			throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 1.23)).getOptionalLong("foo");
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForDefaultLongThrowsFormatException()
			throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 1.23)).getLong("foo", 1L);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForIntThrowsFormatException() throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 1.23)).getInt("foo");
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForOptionalIntThrowsFormatException()
			throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 1.23)).getOptionalInt("foo");
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForDefaultIntThrowsFormatException()
			throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 1.23)).getInt("foo", 1);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForDoubleThrowsFormatException() throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 123)).getDouble("foo");
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForOptionalDoubleThrowsFormatException()
			throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 123)).getOptionalDouble("foo");
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForDefaultDoubleThrowsFormatException()
			throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 123)).getDouble("foo", 1D);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForStringThrowsFormatException() throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 123)).getString("foo");
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForOptionalStringThrowsFormatException()
			throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 123)).getOptionalString("foo");
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForDefaultStringThrowsFormatException()
			throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 123)).getString("foo", "");
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForRawThrowsFormatException() throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 123)).getRaw("foo");
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForOptionalRawThrowsFormatException()
			throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 123)).getOptionalRaw("foo");
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForDefaultRawThrowsFormatException()
			throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 123)).getRaw("foo", new byte[0]);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForListThrowsFormatException()
			throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 123)).getList("foo");
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForOptionalListThrowsFormatException()
			throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 123)).getOptionalList("foo");
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForDefaultListThrowsFormatException()
			throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 123)).getList("foo",
				new BdfList());
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForDictionaryThrowsFormatException()
			throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 123)).getDictionary("foo");
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForOptionalDictionaryThrowsFormatException()
			throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 123)).getOptionalDictionary("foo");
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForDefaultDictionaryThrowsFormatException()
			throws Exception {
		BdfDictionary.of(new BdfEntry("foo", 123)).getDictionary("foo",
				new BdfDictionary());
	}
}
