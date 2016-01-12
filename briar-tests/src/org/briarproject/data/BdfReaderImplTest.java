package org.briarproject.data;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.FormatException;
import org.briarproject.util.StringUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BdfReaderImplTest extends BriarTestCase {

	private BdfReaderImpl r = null;

	@Test
	public void testReadEmptyInput() throws Exception {
		setContents("");
		assertTrue(r.eof());
	}

	@Test
	public void testReadNull() throws Exception {
		setContents("00");
		r.readNull();
		assertTrue(r.eof());
	}

	@Test
	public void testSkipNull() throws Exception {
		setContents("00");
		r.skipNull();
		assertTrue(r.eof());
	}

	@Test
	public void testReadBoolean() throws Exception {
		setContents("10" + "11");
		assertFalse(r.readBoolean());
		assertTrue(r.readBoolean());
		assertTrue(r.eof());
	}

	@Test
	public void testSkipBoolean() throws Exception {
		setContents("10" + "11");
		r.skipBoolean();
		r.skipBoolean();
		assertTrue(r.eof());
	}

	@Test
	public void testReadInt8() throws Exception {
		setContents("21" + "00" + "21" + "FF"
				+ "21" + "7F" + "21" + "80");
		assertEquals(0, r.readInteger());
		assertEquals(-1, r.readInteger());
		assertEquals(Byte.MAX_VALUE, r.readInteger());
		assertEquals(Byte.MIN_VALUE, r.readInteger());
		assertTrue(r.eof());
	}

	@Test
	public void testSkipInt8() throws Exception {
		setContents("21" + "00");
		r.skipInteger();
		assertTrue(r.eof());
	}

	@Test
	public void testReadInt16() throws Exception {
		setContents("22" + "0080" + "22" + "FF7F"
				+ "22" + "7FFF" + "22" + "8000");
		assertEquals(Byte.MAX_VALUE + 1, r.readInteger());
		assertEquals(Byte.MIN_VALUE - 1, r.readInteger());
		assertEquals(Short.MAX_VALUE, r.readInteger());
		assertEquals(Short.MIN_VALUE, r.readInteger());
		assertTrue(r.eof());
	}

	@Test
	public void testSkipInt16() throws Exception {
		setContents("22" + "0080");
		r.skipInteger();
		assertTrue(r.eof());
	}

	@Test
	public void testReadInt32() throws Exception {
		setContents("24" + "00008000" + "24" + "FFFF7FFF"
				+ "24" + "7FFFFFFF" + "24" + "80000000");
		assertEquals(Short.MAX_VALUE + 1, r.readInteger());
		assertEquals(Short.MIN_VALUE - 1, r.readInteger());
		assertEquals(Integer.MAX_VALUE, r.readInteger());
		assertEquals(Integer.MIN_VALUE, r.readInteger());
		assertTrue(r.eof());
	}

	@Test
	public void testSkipInt32() throws Exception {
		setContents("24" + "00008000");
		r.skipInteger();
		assertTrue(r.eof());
	}

	@Test
	public void testReadInt64() throws Exception {
		setContents("28" + "0000000080000000" + "28" + "FFFFFFFF7FFFFFFF"
				+ "28" + "7FFFFFFFFFFFFFFF" + "28" + "8000000000000000");
		assertEquals(Integer.MAX_VALUE + 1L, r.readInteger());
		assertEquals(Integer.MIN_VALUE - 1L, r.readInteger());
		assertEquals(Long.MAX_VALUE, r.readInteger());
		assertEquals(Long.MIN_VALUE, r.readInteger());
		assertTrue(r.eof());
	}

	@Test
	public void testSkipInt64() throws Exception {
		setContents("28" + "0000000080000000");
		r.skipInteger();
		assertTrue(r.eof());
	}

	@Test
	public void testReadFloat() throws Exception {
		// http://babbage.cs.qc.edu/IEEE-754/Decimal.html
		// http://steve.hollasch.net/cgindex/coding/ieeefloat.html
		setContents("38" + "0000000000000000" + "38" + "3FF0000000000000"
				+ "38" + "4000000000000000" + "38" + "BFF0000000000000"
				+ "38" + "8000000000000000" + "38" + "FFF0000000000000"
				+ "38" + "7FF0000000000000" + "38" + "7FF8000000000000");
		assertEquals(0, Double.compare(0.0, r.readFloat()));
		assertEquals(0, Double.compare(1.0, r.readFloat()));
		assertEquals(0, Double.compare(2.0, r.readFloat()));
		assertEquals(0, Double.compare(-1.0, r.readFloat()));
		assertEquals(0, Double.compare(-0.0, r.readFloat()));
		assertEquals(0, Double.compare(Double.NEGATIVE_INFINITY, r.readFloat()));
		assertEquals(0, Double.compare(Double.POSITIVE_INFINITY, r.readFloat()));
		assertTrue(Double.isNaN(r.readFloat()));
		assertTrue(r.eof());
	}

	@Test
	public void testSkipFloat() throws Exception {
		setContents("38" + "0000000000000000");
		r.skipFloat();
		assertTrue(r.eof());
	}

	@Test
	public void testReadString8() throws Exception {
		String longest = TestUtils.createRandomString(Byte.MAX_VALUE);
		String longHex = StringUtils.toHexString(longest.getBytes("UTF-8"));
		// "foo", the empty string, and 127 random letters
		setContents("41" + "03" + "666F6F" + "41" + "00" +
				"41" + "7F" + longHex);
		assertEquals("foo", r.readString(Integer.MAX_VALUE));
		assertEquals("", r.readString(Integer.MAX_VALUE));
		assertEquals(longest, r.readString(Integer.MAX_VALUE));
		assertTrue(r.eof());
	}

	@Test(expected = FormatException.class)
	public void testReadString8ChecksMaxLength() throws Exception {
		// "foo" twice
		setContents("41" + "03" + "666F6F" + "41" + "03" + "666F6F");
		assertEquals("foo", r.readString(3));
		assertTrue(r.hasString());
		r.readString(2);
	}

	@Test
	public void testSkipString8() throws Exception {
		String longest = TestUtils.createRandomString(Byte.MAX_VALUE);
		String longHex = StringUtils.toHexString(longest.getBytes("UTF-8"));
		// "foo", the empty string, and 127 random letters
		setContents("41" + "03" + "666F6F" + "41" + "00" +
				"41" + "7F" + longHex);
		r.skipString();
		r.skipString();
		r.skipString();
		assertTrue(r.eof());
	}

	@Test
	public void testReadString16() throws Exception {
		String shortest = TestUtils.createRandomString(Byte.MAX_VALUE + 1);
		String shortHex = StringUtils.toHexString(shortest.getBytes("UTF-8"));
		String longest = TestUtils.createRandomString(Short.MAX_VALUE);
		String longHex = StringUtils.toHexString(longest.getBytes("UTF-8"));
		// 128 random letters and 2^15 -1 random letters
		setContents("42" + "0080" + shortHex + "42" + "7FFF" + longHex);
		assertEquals(shortest, r.readString(Integer.MAX_VALUE));
		assertEquals(longest, r.readString(Integer.MAX_VALUE));
		assertTrue(r.eof());
	}

	@Test(expected = FormatException.class)
	public void testReadString16ChecksMaxLength() throws Exception {
		String shortest = TestUtils.createRandomString(Byte.MAX_VALUE + 1);
		String shortHex = StringUtils.toHexString(shortest.getBytes("UTF-8"));
		// 128 random letters, twice
		setContents("42" + "0080" + shortHex + "42" + "0080" + shortHex);
		assertEquals(shortest, r.readString(Byte.MAX_VALUE + 1));
		assertTrue(r.hasString());
		r.readString(Byte.MAX_VALUE);
	}

	@Test
	public void testSkipString16() throws Exception {
		String shortest = TestUtils.createRandomString(Byte.MAX_VALUE + 1);
		String shortHex = StringUtils.toHexString(shortest.getBytes("UTF-8"));
		String longest = TestUtils.createRandomString(Short.MAX_VALUE);
		String longHex = StringUtils.toHexString(longest.getBytes("UTF-8"));
		// 128 random letters and 2^15 - 1 random letters
		setContents("42" + "0080" + shortHex + "42" + "7FFF" + longHex);
		r.skipString();
		r.skipString();
		assertTrue(r.eof());
	}

	@Test
	public void testReadString32() throws Exception {
		String shortest = TestUtils.createRandomString(Short.MAX_VALUE + 1);
		String shortHex = StringUtils.toHexString(shortest.getBytes("UTF-8"));
		// 2^15 random letters
		setContents("44" + "00008000" + shortHex);
		assertEquals(shortest, r.readString(Integer.MAX_VALUE));
		assertTrue(r.eof());
	}

	@Test(expected = FormatException.class)
	public void testReadString32ChecksMaxLength() throws Exception {
		String shortest = TestUtils.createRandomString(Short.MAX_VALUE + 1);
		String shortHex = StringUtils.toHexString(shortest.getBytes("UTF-8"));
		// 2^15 random letters, twice
		setContents("44" + "00008000" + shortHex +
				"44" + "00008000" + shortHex);
		assertEquals(shortest, r.readString(Short.MAX_VALUE + 1));
		assertTrue(r.hasString());
		r.readString(Short.MAX_VALUE);
	}

	@Test
	public void testSkipString32() throws Exception {
		String shortest = TestUtils.createRandomString(Short.MAX_VALUE + 1);
		String shortHex = StringUtils.toHexString(shortest.getBytes("UTF-8"));
		// 2^15 random letters, twice
		setContents("44" + "00008000" + shortHex +
				"44" + "00008000" + shortHex);
		r.skipString();
		r.skipString();
		assertTrue(r.eof());
	}

	@Test
	public void testReadRaw8() throws Exception {
		byte[] longest = new byte[Byte.MAX_VALUE];
		String longHex = StringUtils.toHexString(longest);
		// {1, 2, 3}, {}, and 127 zero bytes
		setContents("51" + "03" + "010203" + "51" + "00" +
				"51" + "7F" + longHex);
		assertArrayEquals(new byte[] {1, 2, 3}, r.readRaw(Integer.MAX_VALUE));
		assertArrayEquals(new byte[0], r.readRaw(Integer.MAX_VALUE));
		assertArrayEquals(longest, r.readRaw(Integer.MAX_VALUE));
		assertTrue(r.eof());
	}

	@Test(expected = FormatException.class)
	public void testReadRaw8ChecksMaxLength() throws Exception {
		// {1, 2, 3} twice
		setContents("51" + "03" + "010203" + "51" + "03" + "010203");
		assertArrayEquals(new byte[] {1, 2, 3}, r.readRaw(3));
		assertTrue(r.hasRaw());
		r.readRaw(2);
	}

	@Test
	public void testSkipRaw8() throws Exception {
		byte[] longest = new byte[Byte.MAX_VALUE];
		String longHex = StringUtils.toHexString(longest);
		// {1, 2, 3}, {}, and 127 zero bytes
		setContents("51" + "03" + "010203" + "51" + "00" +
				"51" + "7F" + longHex);
		r.skipRaw();
		r.skipRaw();
		r.skipRaw();
		assertTrue(r.eof());
	}

	@Test
	public void testReadRaw16() throws Exception {
		byte[] shortest = new byte[Byte.MAX_VALUE + 1];
		String shortHex = StringUtils.toHexString(shortest);
		byte[] longest = new byte[Short.MAX_VALUE];
		String longHex = StringUtils.toHexString(longest);
		// 128 zero bytes and 2^15 - 1 zero bytes
		setContents("52" + "0080" + shortHex + "52" + "7FFF" + longHex);
		assertArrayEquals(shortest, r.readRaw(Integer.MAX_VALUE));
		assertArrayEquals(longest, r.readRaw(Integer.MAX_VALUE));
		assertTrue(r.eof());
	}

	@Test(expected = FormatException.class)
	public void testReadRaw16ChecksMaxLength() throws Exception {
		byte[] shortest = new byte[Byte.MAX_VALUE + 1];
		String shortHex = StringUtils.toHexString(shortest);
		// 128 zero bytes, twice
		setContents("52" + "0080" + shortHex + "52" + "0080" + shortHex);
		assertArrayEquals(shortest, r.readRaw(Byte.MAX_VALUE + 1));
		assertTrue(r.hasRaw());
		r.readRaw(Byte.MAX_VALUE);
	}

	@Test
	public void testSkipRaw16() throws Exception {
		byte[] shortest = new byte[Byte.MAX_VALUE + 1];
		String shortHex = StringUtils.toHexString(shortest);
		byte[] longest = new byte[Short.MAX_VALUE];
		String longHex = StringUtils.toHexString(longest);
		// 128 zero bytes and 2^15 - 1 zero bytes
		setContents("52" + "0080" + shortHex + "52" + "7FFF" + longHex);
		r.skipRaw();
		r.skipRaw();
		assertTrue(r.eof());
	}

	@Test
	public void testReadRaw32() throws Exception {
		byte[] shortest = new byte[Short.MAX_VALUE + 1];
		String shortHex = StringUtils.toHexString(shortest);
		// 2^15 zero bytes
		setContents("54" + "00008000" + shortHex);
		assertArrayEquals(shortest, r.readRaw(Integer.MAX_VALUE));
		assertTrue(r.eof());
	}

	@Test(expected = FormatException.class)
	public void testReadRaw32ChecksMaxLength() throws Exception {
		byte[] shortest = new byte[Short.MAX_VALUE + 1];
		String shortHex = StringUtils.toHexString(shortest);
		// 2^15 zero bytes, twice
		setContents("54" + "00008000" + shortHex +
				"54" + "00008000" + shortHex);
		assertArrayEquals(shortest, r.readRaw(Short.MAX_VALUE + 1));
		assertTrue(r.hasRaw());
		r.readRaw(Short.MAX_VALUE);
	}

	@Test
	public void testSkipRaw32() throws Exception {
		byte[] shortest = new byte[Short.MAX_VALUE + 1];
		String shortHex = StringUtils.toHexString(shortest);
		// 2^15 zero bytes, twice
		setContents("54" + "00008000" + shortHex +
				"54" + "00008000" + shortHex);
		r.skipRaw();
		r.skipRaw();
		assertTrue(r.eof());
	}

	@Test
	public void testReadList() throws Exception {
		// A list containing 1, "foo", and 128
		setContents("60" + "21" + "01" +
				"41" + "03" + "666F6F" +
				"22" + "0080" + "80");
		r.readListStart();
		assertFalse(r.hasListEnd());
		assertEquals(1, r.readInteger());
		assertFalse(r.hasListEnd());
		assertEquals("foo", r.readString(1000));
		assertFalse(r.hasListEnd());
		assertEquals(128, r.readInteger());
		assertTrue(r.hasListEnd());
		r.readListEnd();
		assertTrue(r.eof());
	}

	@Test
	public void testSkipList() throws Exception {
		// A list containing 1, "foo", and 128
		setContents("60" + "21" + "01" +
				"41" + "03" + "666F6F" +
				"22" + "0080" + "80");
		r.skipList();
		assertTrue(r.eof());
	}

	@Test
	public void testReadDictionary() throws Exception {
		// A dictionary containing "foo" -> 123 and "bar" -> null
		setContents("70" + "41" + "03" + "666F6F" + "21" + "7B" +
				"41" + "03" + "626172" + "00" + "80");
		r.readDictionaryStart();
		assertFalse(r.hasDictionaryEnd());
		assertEquals("foo", r.readString(1000));
		assertFalse(r.hasDictionaryEnd());
		assertEquals(123, r.readInteger());
		assertFalse(r.hasDictionaryEnd());
		assertEquals("bar", r.readString(1000));
		assertFalse(r.hasDictionaryEnd());
		assertTrue(r.hasNull());
		r.readNull();
		assertTrue(r.hasDictionaryEnd());
		r.readDictionaryEnd();
		assertTrue(r.eof());
	}

	@Test
	public void testSkipDictionary() throws Exception {
		// A map containing "foo" -> 123 and "bar" -> null
		setContents("70" + "41" + "03" + "666F6F" + "21" + "7B" +
				"41" + "03" + "626172" + "00" + "80");
		r.skipDictionary();
		assertTrue(r.eof());
	}

	@Test
	public void testSkipNestedListsAndDictionaries() throws Exception {
		// A list containing a dictionary containing "" -> an empty list
		setContents("60" + "70" + "4100" + "60" + "80" + "80" + "80");
		r.skipList();
		assertTrue(r.eof());
	}

	private void setContents(String hex) {
		ByteArrayInputStream in = new ByteArrayInputStream(
				StringUtils.fromHexString(hex));
		r = new BdfReaderImpl(in);
	}
}
