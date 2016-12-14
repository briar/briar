package org.briarproject.bramble.api.data;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.briarproject.bramble.api.data.BdfDictionary.NULL_VALUE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class BdfListTest extends BrambleTestCase {

	@Test
	public void testConstructors() {
		assertEquals(Collections.emptyList(), new BdfList());
		assertEquals(Arrays.asList(1, 2, NULL_VALUE),
				new BdfList(Arrays.asList(1, 2, NULL_VALUE)));
	}

	@Test
	public void testFactoryMethod() {
		assertEquals(Collections.emptyList(), BdfList.of());
		assertEquals(Arrays.asList(1, 2, NULL_VALUE),
				BdfList.of(1, 2, NULL_VALUE));
	}

	@Test
	public void testIntegerPromotion() throws Exception {
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

	@Test
	@SuppressWarnings("ConstantConditions")
	public void testIndexOutOfBoundsReturnsDefaultValue() throws Exception {
		BdfList list = BdfList.of(1, 2, 3);
		boolean defaultBoolean = true;
		assertEquals(defaultBoolean, list.getBoolean(-1, defaultBoolean));
		assertEquals(defaultBoolean, list.getBoolean(3, defaultBoolean));
		Long defaultLong = 123L;
		assertEquals(defaultLong, list.getLong(-1, defaultLong));
		assertEquals(defaultLong, list.getLong(3, defaultLong));
		Double defaultDouble = 1.23;
		assertEquals(defaultDouble, list.getDouble(-1, defaultDouble));
		assertEquals(defaultDouble, list.getDouble(3, defaultDouble));
		String defaultString = "123";
		assertEquals(defaultString, list.getString(-1, defaultString));
		assertEquals(defaultString, list.getString(3, defaultString));
		byte[] defaultBytes = new byte[] {1, 2, 3};
		assertArrayEquals(defaultBytes, list.getRaw(-1, defaultBytes));
		assertArrayEquals(defaultBytes, list.getRaw(3, defaultBytes));
		BdfList defaultList = BdfList.of(1, 2, 3);
		assertEquals(defaultList, list.getList(-1, defaultList));
		assertEquals(defaultList, list.getList(3, defaultList));
		BdfDictionary defaultDict = BdfDictionary.of(
				new BdfEntry("1", 1),
				new BdfEntry("2", 2),
				new BdfEntry("3", 3)
		);
		assertEquals(defaultDict, list.getDictionary(-1, defaultDict));
		assertEquals(defaultDict, list.getDictionary(3, defaultDict));
	}

	@Test
	@SuppressWarnings("ConstantConditions")
	public void testWrongTypeReturnsDefaultValue() throws Exception {
		BdfList list = BdfList.of(1, 2, 3, true);
		boolean defaultBoolean = true;
		assertEquals(defaultBoolean, list.getBoolean(0, defaultBoolean));
		Long defaultLong = 123L;
		assertEquals(defaultLong, list.getLong(3, defaultLong));
		Double defaultDouble = 1.23;
		assertEquals(defaultDouble, list.getDouble(0, defaultDouble));
		String defaultString = "123";
		assertEquals(defaultString, list.getString(0, defaultString));
		byte[] defaultBytes = new byte[] {1, 2, 3};
		assertArrayEquals(defaultBytes, list.getRaw(0, defaultBytes));
		BdfList defaultList = BdfList.of(1, 2, 3);
		assertEquals(defaultList, list.getList(0, defaultList));
		BdfDictionary defaultDict = BdfDictionary.of(
				new BdfEntry("1", 1),
				new BdfEntry("2", 2),
				new BdfEntry("3", 3)
		);
		assertEquals(defaultDict, list.getDictionary(0, defaultDict));
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
	public void testWrongTypeForLongThrowsFormatException() throws Exception {
		BdfList.of(1.23).getLong(0);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForOptionalLongThrowsFormatException()
			throws Exception {
		BdfList.of(1.23).getOptionalLong(0);
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
	public void testWrongTypeForStringThrowsFormatException() throws Exception {
		BdfList.of(123).getString(0);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForOptionalStringThrowsFormatException()
			throws Exception {
		BdfList.of(123).getOptionalString(0);
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
	public void testWrongTypeForListThrowsFormatException() throws Exception {
		BdfList.of(123).getList(0);
	}

	@Test(expected = FormatException.class)
	public void testWrongTypeForOptionalListThrowsFormatException()
			throws Exception {
		BdfList.of(123).getOptionalList(0);
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
}
