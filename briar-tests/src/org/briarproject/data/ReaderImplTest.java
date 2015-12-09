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

public class ReaderImplTest extends BriarTestCase {

	private ByteArrayInputStream in = null;
	private ReaderImpl r = null;

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
	public void testIntegersMustHaveMinimalLength() throws Exception {
		// INTEGER_16 could be encoded as INTEGER_8
		setContents("21" + "7F" + "22" + "007F");
		assertEquals(Byte.MAX_VALUE, r.readInteger());
		try {
			r.readInteger();
			fail();
		} catch (FormatException expected) {}
		setContents("21" + "80" + "22" + "FF80");
		assertEquals(Byte.MIN_VALUE, r.readInteger());
		try {
			r.readInteger();
			fail();
		} catch (FormatException expected) {}
		// INTEGER_32 could be encoded as INTEGER_16
		setContents("22" + "7FFF" + "24" + "00007FFF");
		assertEquals(Short.MAX_VALUE, r.readInteger());
		try {
			r.readInteger();
			fail();
		} catch (FormatException expected) {}
		setContents("22" + "8000" + "24" + "FFFF8000");
		assertEquals(Short.MIN_VALUE, r.readInteger());
		try {
			r.readInteger();
			fail();
		} catch (FormatException expected) {}
		// INTEGER_64 could be encoded as INTEGER_32
		setContents("24" + "7FFFFFFF" + "28" + "000000007FFFFFFF");
		assertEquals(Integer.MAX_VALUE, r.readInteger());
		try {
			r.readInteger();
			fail();
		} catch (FormatException expected) {}
		setContents("24" + "80000000" + "28" + "FFFFFFFF80000000");
		assertEquals(Integer.MIN_VALUE, r.readInteger());
		try {
			r.readInteger();
			fail();
		} catch (FormatException expected) {}
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

	@Test
	public void testReadString8ChecksMaxLength() throws Exception {
		// "foo" twice
		setContents("41" + "03" + "666F6F" + "41" + "03" + "666F6F");
		assertEquals("foo", r.readString(3));
		assertTrue(r.hasString());
		try {
			r.readString(2);
			fail();
		} catch (FormatException expected) {}
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

	@Test
	public void testReadString16ChecksMaxLength() throws Exception {
		String shortest = TestUtils.createRandomString(Byte.MAX_VALUE + 1);
		String shortHex = StringUtils.toHexString(shortest.getBytes("UTF-8"));
		// 128 random letters, twice
		setContents("42" + "0080" + shortHex + "42" + "0080" + shortHex);
		assertEquals(shortest, r.readString(Byte.MAX_VALUE + 1));
		assertTrue(r.hasString());
		try {
			r.readString(Byte.MAX_VALUE);
			fail();
		} catch (FormatException expected) {}
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

	@Test
	public void testReadString32ChecksMaxLength() throws Exception {
		String shortest = TestUtils.createRandomString(Short.MAX_VALUE + 1);
		String shortHex = StringUtils.toHexString(shortest.getBytes("UTF-8"));
		// 2^15 random letters, twice
		setContents("44" + "00008000" + shortHex +
				"44" + "00008000" + shortHex);
		assertEquals(shortest, r.readString(Short.MAX_VALUE + 1));
		assertTrue(r.hasString());
		try {
			r.readString(Short.MAX_VALUE);
			fail();
		} catch (FormatException expected) {}
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
	public void testStringsMustHaveMinimalLength() throws Exception {
		// STRING_16 could be encoded as STRING_8
		String longest8 = TestUtils.createRandomString(Byte.MAX_VALUE);
		String long8Hex = StringUtils.toHexString(longest8.getBytes("UTF-8"));
		setContents("41" + "7F" + long8Hex + "42" + "007F" + long8Hex);
		assertEquals(longest8, r.readString(Integer.MAX_VALUE));
		try {
			r.readString(Integer.MAX_VALUE);
			fail();
		} catch (FormatException expected) {}
		// STRING_32 could be encoded as STRING_16
		String longest16 = TestUtils.createRandomString(Short.MAX_VALUE);
		String long16Hex = StringUtils.toHexString(longest16.getBytes("UTF-8"));
		setContents("42" + "7FFF" + long16Hex + "44" + "00007FFF" + long16Hex);
		assertEquals(longest16, r.readString(Integer.MAX_VALUE));
		try {
			r.readString(Integer.MAX_VALUE);
			fail();
		} catch (FormatException expected) {}
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

	@Test
	public void testReadRaw8ChecksMaxLength() throws Exception {
		// {1, 2, 3} twice
		setContents("51" + "03" + "010203" + "51" + "03" + "010203");
		assertArrayEquals(new byte[] {1, 2, 3}, r.readRaw(3));
		assertTrue(r.hasRaw());
		try {
			r.readRaw(2);
			fail();
		} catch (FormatException expected) {}
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

	@Test
	public void testReadRaw16ChecksMaxLength() throws Exception {
		byte[] shortest = new byte[Byte.MAX_VALUE + 1];
		String shortHex = StringUtils.toHexString(shortest);
		// 128 zero bytes, twice
		setContents("52" + "0080" + shortHex + "52" + "0080" + shortHex);
		assertArrayEquals(shortest, r.readRaw(Byte.MAX_VALUE + 1));
		assertTrue(r.hasRaw());
		try {
			r.readRaw(Byte.MAX_VALUE);
			fail();
		} catch (FormatException expected) {}
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

	@Test
	public void testReadRaw32ChecksMaxLength() throws Exception {
		byte[] shortest = new byte[Short.MAX_VALUE + 1];
		String shortHex = StringUtils.toHexString(shortest);
		// 2^15 zero bytes, twice
		setContents("54" + "00008000" + shortHex +
				"54" + "00008000" + shortHex);
		assertArrayEquals(shortest, r.readRaw(Short.MAX_VALUE + 1));
		assertTrue(r.hasRaw());
		try {
			r.readRaw(Short.MAX_VALUE);
			fail();
		} catch (FormatException expected) {}
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
	public void testRawMustHaveMinimalLength() throws Exception {
		// RAW_16 could be encoded as RAW_8
		byte[] longest8 = new byte[Byte.MAX_VALUE];
		String long8Hex = StringUtils.toHexString(longest8);
		setContents("51" + "7F" + long8Hex + "52" + "007F" + long8Hex);
		assertArrayEquals(longest8, r.readRaw(Integer.MAX_VALUE));
		try {
			r.readRaw(Integer.MAX_VALUE);
			fail();
		} catch (FormatException expected) {}
		// RAW_32 could be encoded as RAW_16
		byte[] longest16 = new byte[Short.MAX_VALUE];
		String long16Hex = StringUtils.toHexString(longest16);
		setContents("52" + "7FFF" + long16Hex + "54" + "00007FFF" + long16Hex);
		assertArrayEquals(longest16, r.readRaw(Integer.MAX_VALUE));
		try {
			r.readRaw(Integer.MAX_VALUE);
			fail();
		} catch (FormatException expected) {}
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
	public void testReadMap() throws Exception {
		// A map containing "foo" -> 123 and "bar" -> null
		setContents("70" + "41" + "03" + "666F6F" + "21" + "7B" +
				"41" + "03" + "626172" + "00" + "80");
		r.readMapStart();
		assertFalse(r.hasMapEnd());
		assertEquals("foo", r.readString(1000));
		assertFalse(r.hasMapEnd());
		assertEquals(123, r.readInteger());
		assertFalse(r.hasMapEnd());
		assertEquals("bar", r.readString(1000));
		assertFalse(r.hasMapEnd());
		assertTrue(r.hasNull());
		r.readNull();
		assertTrue(r.hasMapEnd());
		r.readMapEnd();
		assertTrue(r.eof());
	}

	@Test
	public void testSkipMap() throws Exception {
		// A map containing "foo" -> 123 and "bar" -> null
		setContents("70" + "41" + "03" + "666F6F" + "21" + "7B" +
				"41" + "03" + "626172" + "00" + "80");
		r.skipMap();
		assertTrue(r.eof());
	}

	@Test
	public void testSkipNestedListsAndMaps() throws Exception {
		// A list containing a map containing two empty lists
		setContents("60" + "70" + "60" + "80" + "60" + "80" + "80" + "80");
		r.skipList();
		assertTrue(r.eof());
	}

	private void setContents(String hex) {
		in = new ByteArrayInputStream(StringUtils.fromHexString(hex));
		r = new ReaderImpl(in);
	}
}
