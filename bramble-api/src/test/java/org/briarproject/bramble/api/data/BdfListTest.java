package org.briarproject.bramble.api.data;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import static java.lang.Boolean.TRUE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.briarproject.bramble.api.data.BdfDictionary.NULL_VALUE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class BdfListTest extends BrambleTestCase {

	@Test
	public void testConstructors() {
		assertEquals(emptyList(), new BdfList());
		assertEquals(asList(1, 2, NULL_VALUE),
				new BdfList(asList(1, 2, NULL_VALUE)));
	}

	@Test
	public void testFactoryMethod() {
		assertEquals(emptyList(), BdfList.of());
		assertEquals(asList(1, 2, NULL_VALUE), BdfList.of(1, 2, NULL_VALUE));
	}

	@Test
	public void testLongPromotion() throws Exception {
		BdfList list = new BdfList();
		list.add((byte) 1);
		list.add((short) 2);
		list.add(3);
		list.add(4L);
		assertEquals(Long.valueOf(1), list.getLong(0));
		assertEquals(Long.valueOf(2), list.getLong(1));
		assertEquals(Long.valueOf(3), list.getLong(2));
		assertEquals(Long.valueOf(4), list.getLong(3));
	}

	@Test
	public void testIntPromotionAndDemotion() throws Exception {
		BdfList list = new BdfList();
		list.add((byte) 1);
		list.add((short) 2);
		list.add(3);
		list.add(4L);
		assertEquals(Integer.valueOf(1), list.getInt(0));
		assertEquals(Integer.valueOf(2), list.getInt(1));
		assertEquals(Integer.valueOf(3), list.getInt(2));
		assertEquals(Integer.valueOf(4), list.getInt(3));
	}

	@Test(expected = FormatException.class)
	public void testIntUnderflow() throws Exception {
		BdfList list = BdfList.of(Integer.MIN_VALUE - 1L);
		list.getInt(0);
	}

	@Test(expected = FormatException.class)
	public void testIntOverflow() throws Exception {
		BdfList list = BdfList.of(Integer.MAX_VALUE + 1L);
		list.getInt(0);
	}

	@Test
	public void testFloatPromotion() throws Exception {
		BdfList list = new BdfList();
		list.add(1F);
		list.add(2D);
		assertEquals(Double.valueOf(1), list.getDouble(0));
		assertEquals(Double.valueOf(2), list.getDouble(1));
	}

	@Test
	public void testByteArrayUnwrapping() throws Exception {
		BdfList list = new BdfList();
		list.add(new byte[123]);
		list.add(new Bytes(new byte[123]));
		byte[] first = list.getRaw(0);
		assertEquals(123, first.length);
		assertArrayEquals(new byte[123], first);
		byte[] second = list.getRaw(1);
		assertEquals(123, second.length);
		assertArrayEquals(new byte[123], second);
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForBooleanThrowsFormatException()
			throws Exception {
		new BdfList().getBoolean(-1);
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForOptionalBooleanThrowsFormatException()
			throws Exception {
		new BdfList().getOptionalBoolean(-1);
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForDefaultBooleanThrowsFormatException()
			throws Exception {
		new BdfList().getBoolean(-1, true);
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForLongThrowsFormatException()
			throws Exception {
		new BdfList().getLong(-1);
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForOptionalLongThrowsFormatException()
			throws Exception {
		new BdfList().getOptionalLong(-1);
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForDefaultLongThrowsFormatException()
			throws Exception {
		new BdfList().getLong(-1, 1L);
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForIntThrowsFormatException()
			throws Exception {
		new BdfList().getInt(-1);
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForOptionalIntThrowsFormatException()
			throws Exception {
		new BdfList().getOptionalInt(-1);
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForDefaultIntThrowsFormatException()
			throws Exception {
		new BdfList().getInt(-1, 1);
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForDoubleThrowsFormatException()
			throws Exception {
		new BdfList().getDouble(-1);
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForOptionalDoubleThrowsFormatException()
			throws Exception {
		new BdfList().getOptionalDouble(-1);
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForDefaultDoubleThrowsFormatException()
			throws Exception {
		new BdfList().getDouble(-1, 1D);
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForStringThrowsFormatException()
			throws Exception {
		new BdfList().getString(-1);
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForOptionalStringThrowsFormatException()
			throws Exception {
		new BdfList().getOptionalString(-1);
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForDefaultStringThrowsFormatException()
			throws Exception {
		new BdfList().getString(-1, "");
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForRawThrowsFormatException()
			throws Exception {
		new BdfList().getRaw(-1);
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForOptionalRawThrowsFormatException()
			throws Exception {
		new BdfList().getOptionalRaw(-1);
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForDefaultRawThrowsFormatException()
			throws Exception {
		new BdfList().getRaw(-1, new byte[0]);
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForListThrowsFormatException()
			throws Exception {
		new BdfList().getList(-1);
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForOptionalListThrowsFormatException()
			throws Exception {
		new BdfList().getOptionalList(-1);
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForDefaultListThrowsFormatException()
			throws Exception {
		new BdfList().getList(-1, new BdfList());
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForDictionaryThrowsFormatException()
			throws Exception {
		new BdfList().getDictionary(-1);
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForOptionalDictionaryThrowsFormatException()
			throws Exception {
		new BdfList().getOptionalDictionary(-1);
	}

	@Test(expected = FormatException.class)
	public void testNegativeIndexForDefaultDictionaryThrowsFormatException()
			throws Exception {
		new BdfList().getDictionary(-1, new BdfDictionary());
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForBooleanThrowsFormatException()
			throws Exception {
		new BdfList().getBoolean(0);
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForOptionalBooleanThrowsFormatException()
			throws Exception {
		new BdfList().getOptionalBoolean(0);
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForDefaultBooleanThrowsFormatException()
			throws Exception {
		new BdfList().getBoolean(0, true);
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForLongThrowsFormatException()
			throws Exception {
		new BdfList().getLong(0);
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForOptionalLongThrowsFormatException()
			throws Exception {
		new BdfList().getOptionalLong(0);
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForDefaultLongThrowsFormatException()
			throws Exception {
		new BdfList().getLong(0, 1L);
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForIntThrowsFormatException()
			throws Exception {
		new BdfList().getInt(0);
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForOptionalIntThrowsFormatException()
			throws Exception {
		new BdfList().getOptionalInt(0);
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForDefaultIntThrowsFormatException()
			throws Exception {
		new BdfList().getInt(0, 1);
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForDoubleThrowsFormatException()
			throws Exception {
		new BdfList().getDouble(0);
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForOptionalDoubleThrowsFormatException()
			throws Exception {
		new BdfList().getOptionalDouble(0);
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForDefaultDoubleThrowsFormatException()
			throws Exception {
		new BdfList().getDouble(0, 1D);
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForStringThrowsFormatException()
			throws Exception {
		new BdfList().getString(0);
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForOptionalStringThrowsFormatException()
			throws Exception {
		new BdfList().getOptionalString(0);
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForDefaultStringThrowsFormatException()
			throws Exception {
		new BdfList().getString(0, "");
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForRawThrowsFormatException()
			throws Exception {
		new BdfList().getRaw(0);
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForOptionalRawThrowsFormatException()
			throws Exception {
		new BdfList().getOptionalRaw(0);
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForDefaultRawThrowsFormatException()
			throws Exception {
		new BdfList().getRaw(0, new byte[0]);
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForListThrowsFormatException()
			throws Exception {
		new BdfList().getList(0);
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForOptionalListThrowsFormatException()
			throws Exception {
		new BdfList().getOptionalList(0);
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForDefaultListThrowsFormatException()
			throws Exception {
		new BdfList().getList(0, new BdfList());
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForDictionaryThrowsFormatException()
			throws Exception {
		new BdfList().getDictionary(0);
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForOptionalDictionaryThrowsFormatException()
			throws Exception {
		new BdfList().getOptionalDictionary(0);
	}

	@Test(expected = FormatException.class)
	public void testTooLargeIndexForDefaultDictionaryThrowsFormatException()
			throws Exception {
		new BdfList().getDictionary(0, new BdfDictionary());
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForBooleanThrowsFormatException()
			throws Exception {
		BdfList.of(123).getBoolean(0);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForOptionalBooleanThrowsFormatException()
			throws Exception {
		BdfList.of(123).getOptionalBoolean(0);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForDefaultBooleanThrowsFormatException()
			throws Exception {
		BdfList.of(123).getBoolean(0, true);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForLongThrowsFormatException() throws Exception {
		BdfList.of(1.23).getLong(0);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForOptionalLongThrowsFormatException()
			throws Exception {
		BdfList.of(1.23).getOptionalLong(0);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForDefaultLongThrowsFormatException()
			throws Exception {
		BdfList.of(1.23).getLong(0, 1L);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForIntThrowsFormatException() throws Exception {
		BdfList.of(1.23).getInt(0);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForOptionalIntThrowsFormatException()
			throws Exception {
		BdfList.of(1.23).getOptionalInt(0);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForDefaultIntThrowsFormatException()
			throws Exception {
		BdfList.of(1.23).getInt(0, 1);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForDoubleThrowsFormatException() throws Exception {
		BdfList.of(123).getDouble(0);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForOptionalDoubleThrowsFormatException()
			throws Exception {
		BdfList.of(123).getOptionalDouble(0);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForDefaultDoubleThrowsFormatException()
			throws Exception {
		BdfList.of(123).getDouble(0, 1D);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForStringThrowsFormatException() throws Exception {
		BdfList.of(123).getString(0);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForOptionalStringThrowsFormatException()
			throws Exception {
		BdfList.of(123).getOptionalString(0);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForDefaultStringThrowsFormatException()
			throws Exception {
		BdfList.of(123).getString(0, "");
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForRawThrowsFormatException() throws Exception {
		BdfList.of(123).getRaw(0);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForOptionalRawThrowsFormatException()
			throws Exception {
		BdfList.of(123).getOptionalRaw(0);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForDefaultRawThrowsFormatException()
			throws Exception {
		BdfList.of(123).getRaw(0, new byte[0]);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForListThrowsFormatException() throws Exception {
		BdfList.of(123).getList(0);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForOptionalListThrowsFormatException()
			throws Exception {
		BdfList.of(123).getOptionalList(0);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForDefaultListThrowsFormatException()
			throws Exception {
		BdfList.of(123).getList(0, new BdfList());
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForDictionaryThrowsFormatException()
			throws Exception {
		BdfList.of(123).getDictionary(0);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForOptionalDictionaryThrowsFormatException()
			throws Exception {
		BdfList.of(123).getOptionalDictionary(0);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForDefaultDictionaryThrowsFormatException()
			throws Exception {
		BdfList.of(123).getDictionary(0, new BdfDictionary());
	}

	@Test(expected = FormatException.class)
	public void testNullValueForBooleanThrowsFormatException()
			throws Exception {
		BdfList.of(NULL_VALUE).getBoolean(0);
	}

	@Test(expected = FormatException.class)
	public void testNullValueForLongThrowsFormatException() throws Exception {
		BdfList.of(NULL_VALUE).getLong(0);
	}

	@Test(expected = FormatException.class)
	public void testNullValueForIntThrowsFormatException() throws Exception {
		BdfList.of(NULL_VALUE).getInt(0);
	}

	@Test(expected = FormatException.class)
	public void testNullValueForDoubleThrowsFormatException() throws Exception {
		BdfList.of(NULL_VALUE).getDouble(0);
	}

	@Test(expected = FormatException.class)
	public void testNullValueForStringThrowsFormatException() throws Exception {
		BdfList.of(NULL_VALUE).getString(0);
	}

	@Test(expected = FormatException.class)
	public void testNullValueForRawThrowsFormatException() throws Exception {
		BdfList.of(NULL_VALUE).getRaw(0);
	}

	@Test(expected = FormatException.class)
	public void testNullValueForListThrowsFormatException() throws Exception {
		BdfList.of(NULL_VALUE).getList(0);
	}

	@Test(expected = FormatException.class)
	public void testNullValueForDictionaryThrowsFormatException()
			throws Exception {
		BdfList.of(NULL_VALUE).getDictionary(0);
	}

	@Test
	public void testOptionalMethodsReturnNullForNullValue() throws Exception {
		BdfList list = BdfList.of(NULL_VALUE);
		assertNull(list.getOptionalBoolean(0));
		assertNull(list.getOptionalLong(0));
		assertNull(list.getOptionalInt(0));
		assertNull(list.getOptionalDouble(0));
		assertNull(list.getOptionalString(0));
		assertNull(list.getOptionalRaw(0));
		assertNull(list.getOptionalList(0));
		assertNull(list.getOptionalDictionary(0));
	}

	@Test
	public void testDefaultMethodsReturnDefaultForNullValue() throws Exception {
		BdfList list = BdfList.of(NULL_VALUE);
		assertEquals(TRUE, list.getBoolean(0, TRUE));
		assertEquals(Long.valueOf(123L), list.getLong(0, 123L));
		assertEquals(Integer.valueOf(123), list.getInt(0, 123));
		assertEquals(Double.valueOf(123D), list.getDouble(0, 123D));
		assertEquals("123", list.getString(0, "123"));
		byte[] defaultRaw = {1, 2, 3};
		assertArrayEquals(defaultRaw, list.getRaw(0, defaultRaw));
		BdfList defaultList = BdfList.of(1, 2, 3);
		assertEquals(defaultList, list.getList(0, defaultList));
		BdfDictionary defaultDict = BdfDictionary.of(new BdfEntry("123", 123));
		assertEquals(defaultDict, list.getDictionary(0, defaultDict));
	}
}
