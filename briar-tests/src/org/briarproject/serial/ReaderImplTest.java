package org.briarproject.serial;

import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.FormatException;
import org.briarproject.util.StringUtils;
import org.junit.Test;

public class ReaderImplTest extends BriarTestCase {

	private ByteArrayInputStream in = null;
	private ReaderImpl r = null;

	@Test
	public void testReadBoolean() throws Exception {
		setContents("00" + "01");
		assertFalse(r.readBoolean());
		assertTrue(r.readBoolean());
		assertTrue(r.eof());
	}

	@Test
	public void testSkipBoolean() throws Exception {
		setContents("00" + "01");
		r.skipBoolean();
		r.skipBoolean();
		assertTrue(r.eof());
	}

	@Test
	public void testReadInt8() throws Exception {
		setContents("02" + "00" + "02" + "FF"
				+ "02" + "7F" + "02" + "80");
		assertEquals(0, r.readInteger());
		assertEquals(-1, r.readInteger());
		assertEquals(Byte.MAX_VALUE, r.readInteger());
		assertEquals(Byte.MIN_VALUE, r.readInteger());
		assertTrue(r.eof());
	}

	@Test
	public void testSkipInt8() throws Exception {
		setContents("02" + "00");
		r.skipInteger();
		assertTrue(r.eof());
	}

	@Test
	public void testReadInt16() throws Exception {
		setContents("03" + "0080" + "03" + "FF7F"
				+ "03" + "7FFF" + "03" + "8000");
		assertEquals(Byte.MAX_VALUE + 1, r.readInteger());
		assertEquals(Byte.MIN_VALUE - 1, r.readInteger());
		assertEquals(Short.MAX_VALUE, r.readInteger());
		assertEquals(Short.MIN_VALUE, r.readInteger());
		assertTrue(r.eof());
	}

	@Test
	public void testSkipInt16() throws Exception {
		setContents("03" + "0080");
		r.skipInteger();
		assertTrue(r.eof());
	}

	@Test
	public void testReadInt32() throws Exception {
		setContents("04" + "00008000" + "04" + "FFFF7FFF"
				+ "04" + "7FFFFFFF" + "04" + "80000000");
		assertEquals(Short.MAX_VALUE + 1, r.readInteger());
		assertEquals(Short.MIN_VALUE - 1, r.readInteger());
		assertEquals(Integer.MAX_VALUE, r.readInteger());
		assertEquals(Integer.MIN_VALUE, r.readInteger());
		assertTrue(r.eof());
	}

	@Test
	public void testSkipInt32() throws Exception {
		setContents("04" + "00008000");
		r.skipInteger();
		assertTrue(r.eof());
	}

	@Test
	public void testReadInt64() throws Exception {
		setContents("05" + "0000000080000000" + "05" + "FFFFFFFF7FFFFFFF"
				+ "05" + "7FFFFFFFFFFFFFFF" + "05" + "8000000000000000");
		assertEquals(Integer.MAX_VALUE + 1L, r.readInteger());
		assertEquals(Integer.MIN_VALUE - 1L, r.readInteger());
		assertEquals(Long.MAX_VALUE, r.readInteger());
		assertEquals(Long.MIN_VALUE, r.readInteger());
		assertTrue(r.eof());
	}

	@Test
	public void testSkipInt64() throws Exception {
		setContents("05" + "0000000080000000");
		r.skipInteger();
		assertTrue(r.eof());
	}

	@Test
	public void testIntegersMustHaveMinimalLength() throws Exception {
		// INTEGER_16 could be encoded as INTEGER_8
		setContents("02" + "7F" + "03" + "007F");
		assertEquals(Byte.MAX_VALUE, r.readInteger());
		try {
			r.readInteger();
			fail();
		} catch(FormatException expected) {}
		setContents("02" + "80" + "03" + "FF80");
		assertEquals(Byte.MIN_VALUE, r.readInteger());
		try {
			r.readInteger();
			fail();
		} catch(FormatException expected) {}
		// INTEGER_32 could be encoded as INTEGER_16
		setContents("03" + "7FFF" + "04" + "00007FFF");
		assertEquals(Short.MAX_VALUE, r.readInteger());
		try {
			r.readInteger();
			fail();
		} catch(FormatException expected) {}
		setContents("03" + "8000" + "04" + "FFFF8000");
		assertEquals(Short.MIN_VALUE, r.readInteger());
		try {
			r.readInteger();
			fail();
		} catch(FormatException expected) {}
		// INTEGER_64 could be encoded as INTEGER_32
		setContents("04" + "7FFFFFFF" + "05" + "000000007FFFFFFF");
		assertEquals(Integer.MAX_VALUE, r.readInteger());
		try {
			r.readInteger();
			fail();
		} catch(FormatException expected) {}
		setContents("04" + "80000000" + "05" + "FFFFFFFF80000000");
		assertEquals(Integer.MIN_VALUE, r.readInteger());
		try {
			r.readInteger();
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testReadFloat() throws Exception {
		// http://babbage.cs.qc.edu/IEEE-754/Decimal.html
		// http://steve.hollasch.net/cgindex/coding/ieeefloat.html
		setContents("06" + "0000000000000000" + "06" + "3FF0000000000000"
				+ "06" + "4000000000000000" + "06" + "BFF0000000000000"
				+ "06" + "8000000000000000" + "06" + "FFF0000000000000"
				+ "06" + "7FF0000000000000" + "06" + "7FF8000000000000");
		assertEquals(0.0, r.readFloat());
		assertEquals(1.0, r.readFloat());
		assertEquals(2.0, r.readFloat());
		assertEquals(-1.0, r.readFloat());
		assertEquals(-0.0, r.readFloat());
		assertEquals(Double.NEGATIVE_INFINITY, r.readFloat());
		assertEquals(Double.POSITIVE_INFINITY, r.readFloat());
		assertTrue(Double.isNaN(r.readFloat()));
		assertTrue(r.eof());
	}

	@Test
	public void testSkipFloat() throws Exception {
		setContents("06" + "0000000000000000");
		r.skipFloat();
		assertTrue(r.eof());
	}

	@Test
	public void testReadString8() throws Exception {
		String longest = TestUtils.createRandomString(Byte.MAX_VALUE);
		String longHex = StringUtils.toHexString(longest.getBytes("UTF-8"));
		// "foo", the empty string, and 127 random letters
		setContents("07" + "03" + "666F6F" + "07" + "00" +
				"07" + "7F" + longHex);
		assertEquals("foo", r.readString(Integer.MAX_VALUE));
		assertEquals("", r.readString(Integer.MAX_VALUE));
		assertEquals(longest, r.readString(Integer.MAX_VALUE));
		assertTrue(r.eof());
	}

	@Test
	public void testReadString8ChecksMaxLength() throws Exception {
		// "foo" twice
		setContents("07" + "03" + "666F6F" + "07" + "03" + "666F6F");
		assertEquals("foo", r.readString(3));
		assertTrue(r.hasString());
		try {
			r.readString(2);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testSkipString8() throws Exception {
		String longest = TestUtils.createRandomString(Byte.MAX_VALUE);
		String longHex = StringUtils.toHexString(longest.getBytes("UTF-8"));
		// "foo", the empty string, and 127 random letters
		setContents("07" + "03" + "666F6F" + "07" + "00" +
				"07" + "7F" + longHex);
		r.skipString(Integer.MAX_VALUE);
		r.skipString(Integer.MAX_VALUE);
		r.skipString(Integer.MAX_VALUE);
		assertTrue(r.eof());
	}

	@Test
	public void testSkipString8ChecksMaxLength() throws Exception {
		// "foo" twice
		setContents("07" + "03" + "666F6F" + "07" + "03" + "666F6F");
		r.skipString(3);
		assertTrue(r.hasString());
		try {
			r.skipString(2);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testReadString16() throws Exception {
		String shortest = TestUtils.createRandomString(Byte.MAX_VALUE + 1);
		String shortHex = StringUtils.toHexString(shortest.getBytes("UTF-8"));
		String longest = TestUtils.createRandomString(Short.MAX_VALUE);
		String longHex = StringUtils.toHexString(longest.getBytes("UTF-8"));
		// 128 random letters and 2^15 -1 random letters
		setContents("08" + "0080" + shortHex + "08" + "7FFF" + longHex);
		assertEquals(shortest, r.readString(Integer.MAX_VALUE));
		assertEquals(longest, r.readString(Integer.MAX_VALUE));
		assertTrue(r.eof());
	}

	@Test
	public void testReadString16ChecksMaxLength() throws Exception {
		String shortest = TestUtils.createRandomString(Byte.MAX_VALUE + 1);
		String shortHex = StringUtils.toHexString(shortest.getBytes("UTF-8"));
		// 128 random letters, twice
		setContents("08" + "0080" + shortHex + "08" + "0080" + shortHex);
		assertEquals(shortest, r.readString(Byte.MAX_VALUE + 1));
		assertTrue(r.hasString());
		try {
			r.readString(Byte.MAX_VALUE);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testSkipString16() throws Exception {
		String shortest = TestUtils.createRandomString(Byte.MAX_VALUE + 1);
		String shortHex = StringUtils.toHexString(shortest.getBytes("UTF-8"));
		String longest = TestUtils.createRandomString(Short.MAX_VALUE);
		String longHex = StringUtils.toHexString(longest.getBytes("UTF-8"));
		// 128 random letters and 2^15 - 1 random letters
		setContents("08" + "0080" + shortHex + "08" + "7FFF" + longHex);
		r.skipString(Integer.MAX_VALUE);
		r.skipString(Integer.MAX_VALUE);
		assertTrue(r.eof());
	}

	@Test
	public void testSkipString16ChecksMaxLength() throws Exception {
		String shortest = TestUtils.createRandomString(Byte.MAX_VALUE + 1);
		String shortHex = StringUtils.toHexString(shortest.getBytes("UTF-8"));
		// 128 random letters, twice
		setContents("08" + "0080" + shortHex + "08" + "0080" + shortHex);
		r.skipString(Byte.MAX_VALUE + 1);
		assertTrue(r.hasString());
		try {
			r.skipString(Byte.MAX_VALUE);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testReadString32() throws Exception {
		String shortest = TestUtils.createRandomString(Short.MAX_VALUE + 1);
		String shortHex = StringUtils.toHexString(shortest.getBytes("UTF-8"));
		// 2^15 random letters
		setContents("09" + "00008000" + shortHex);
		assertEquals(shortest, r.readString(Integer.MAX_VALUE));
		assertTrue(r.eof());
	}

	@Test
	public void testReadString32ChecksMaxLength() throws Exception {
		String shortest = TestUtils.createRandomString(Short.MAX_VALUE + 1);
		String shortHex = StringUtils.toHexString(shortest.getBytes("UTF-8"));
		// 2^15 random letters, twice
		setContents("09" + "00008000" + shortHex +
				"09" + "00008000" + shortHex);
		assertEquals(shortest, r.readString(Short.MAX_VALUE + 1));
		assertTrue(r.hasString());
		try {
			r.readString(Short.MAX_VALUE);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testSkipString32() throws Exception {
		String shortest = TestUtils.createRandomString(Short.MAX_VALUE + 1);
		String shortHex = StringUtils.toHexString(shortest.getBytes("UTF-8"));
		// 2^15 random letters, twice
		setContents("09" + "00008000" + shortHex +
				"09" + "00008000" + shortHex);
		r.skipString(Integer.MAX_VALUE);
		r.skipString(Integer.MAX_VALUE);
		assertTrue(r.eof());
	}

	@Test
	public void testSkipString32ChecksMaxLength() throws Exception {
		String shortest = TestUtils.createRandomString(Short.MAX_VALUE + 1);
		String shortHex = StringUtils.toHexString(shortest.getBytes("UTF-8"));
		// 2^15 random letters, twice
		setContents("09" + "00008000" + shortHex +
				"09" + "00008000" + shortHex);
		r.skipString(Short.MAX_VALUE + 1);
		assertTrue(r.hasString());
		try {
			r.skipString(Short.MAX_VALUE);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testStringsMustHaveMinimalLength() throws Exception {
		// STRING_16 could be encoded as STRING_8
		String longest8 = TestUtils.createRandomString(Byte.MAX_VALUE);
		String long8Hex = StringUtils.toHexString(longest8.getBytes("UTF-8"));
		setContents("07" + "7F" + long8Hex + "08" + "007F" + long8Hex);
		assertEquals(longest8, r.readString(Integer.MAX_VALUE));
		try {
			r.readString(Integer.MAX_VALUE);
			fail();
		} catch(FormatException expected) {}
		// STRING_32 could be encoded as STRING_16
		String longest16 = TestUtils.createRandomString(Short.MAX_VALUE);
		String long16Hex = StringUtils.toHexString(longest16.getBytes("UTF-8"));
		setContents("08" + "7FFF" + long16Hex + "09" + "00007FFF" + long16Hex);
		assertEquals(longest16, r.readString(Integer.MAX_VALUE));
		try {
			r.readString(Integer.MAX_VALUE);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testReadBytes8() throws Exception {
		byte[] longest = new byte[Byte.MAX_VALUE];
		String longHex = StringUtils.toHexString(longest);
		// {1, 2, 3}, {}, and 127 zero bytes
		setContents("0A" + "03" + "010203" + "0A" + "00" +
				"0A" + "7F" + longHex);
		assertArrayEquals(new byte[] {1, 2, 3}, r.readBytes(Integer.MAX_VALUE));
		assertArrayEquals(new byte[0], r.readBytes(Integer.MAX_VALUE));
		assertArrayEquals(longest, r.readBytes(Integer.MAX_VALUE));
		assertTrue(r.eof());
	}

	@Test
	public void testReadBytes8ChecksMaxLength() throws Exception {
		// {1, 2, 3} twice
		setContents("0A" + "03" + "010203" + "0A" + "03" + "010203");
		assertArrayEquals(new byte[] {1, 2, 3}, r.readBytes(3));
		assertTrue(r.hasBytes());
		try {
			r.readBytes(2);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testSkipBytes8() throws Exception {
		byte[] longest = new byte[Byte.MAX_VALUE];
		String longHex = StringUtils.toHexString(longest);
		// {1, 2, 3}, {}, and 127 zero bytes
		setContents("0A" + "03" + "010203" + "0A" + "00" +
				"0A" + "7F" + longHex);
		r.skipBytes(Integer.MAX_VALUE);
		r.skipBytes(Integer.MAX_VALUE);
		r.skipBytes(Integer.MAX_VALUE);
		assertTrue(r.eof());
	}

	@Test
	public void testSkipBytes8ChecksMaxLength() throws Exception {
		// {1, 2, 3} twice
		setContents("0A" + "03" + "010203" + "0A" + "03" + "010203");
		r.skipBytes(3);
		assertTrue(r.hasBytes());
		try {
			r.skipBytes(2);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testReadBytes16() throws Exception {
		byte[] shortest = new byte[Byte.MAX_VALUE + 1];
		String shortHex = StringUtils.toHexString(shortest);
		byte[] longest = new byte[Short.MAX_VALUE];
		String longHex = StringUtils.toHexString(longest);
		// 128 zero bytes and 2^15 - 1 zero bytes
		setContents("0B" + "0080" + shortHex + "0B" + "7FFF" + longHex);
		assertArrayEquals(shortest, r.readBytes(Integer.MAX_VALUE));
		assertArrayEquals(longest, r.readBytes(Integer.MAX_VALUE));
		assertTrue(r.eof());
	}

	@Test
	public void testReadBytes16ChecksMaxLength() throws Exception {
		byte[] shortest = new byte[Byte.MAX_VALUE + 1];
		String shortHex = StringUtils.toHexString(shortest);
		// 128 zero bytes, twice
		setContents("0B" + "0080" + shortHex + "0B" + "0080" + shortHex);
		assertArrayEquals(shortest, r.readBytes(Byte.MAX_VALUE + 1));
		assertTrue(r.hasBytes());
		try {
			r.readBytes(Byte.MAX_VALUE);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testSkipBytes16() throws Exception {
		byte[] shortest = new byte[Byte.MAX_VALUE + 1];
		String shortHex = StringUtils.toHexString(shortest);
		byte[] longest = new byte[Short.MAX_VALUE];
		String longHex = StringUtils.toHexString(longest);
		// 128 zero bytes and 2^15 - 1 zero bytes
		setContents("0B" + "0080" + shortHex + "0B" + "7FFF" + longHex);
		r.skipBytes(Integer.MAX_VALUE);
		r.skipBytes(Integer.MAX_VALUE);
		assertTrue(r.eof());
	}

	@Test
	public void testSkipBytes16ChecksMaxLength() throws Exception {
		byte[] shortest = new byte[Byte.MAX_VALUE + 1];
		String shortHex = StringUtils.toHexString(shortest);
		// 128 zero bytes, twice
		setContents("0B" + "0080" + shortHex + "0B" + "0080" + shortHex);
		r.skipBytes(Byte.MAX_VALUE + 1);
		assertTrue(r.hasBytes());
		try {
			r.skipBytes(Byte.MAX_VALUE);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testReadBytes32() throws Exception {
		byte[] shortest = new byte[Short.MAX_VALUE + 1];
		String shortHex = StringUtils.toHexString(shortest);
		// 2^15 zero bytes
		setContents("0C" + "00008000" + shortHex);
		assertArrayEquals(shortest, r.readBytes(Integer.MAX_VALUE));
		assertTrue(r.eof());
	}

	@Test
	public void testReadBytes32ChecksMaxLength() throws Exception {
		byte[] shortest = new byte[Short.MAX_VALUE + 1];
		String shortHex = StringUtils.toHexString(shortest);
		// 2^15 zero bytes, twice
		setContents("0C" + "00008000" + shortHex +
				"0C" + "00008000" + shortHex);
		assertArrayEquals(shortest, r.readBytes(Short.MAX_VALUE + 1));
		assertTrue(r.hasBytes());
		try {
			r.readBytes(Short.MAX_VALUE);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testSkipBytes32() throws Exception {
		byte[] shortest = new byte[Short.MAX_VALUE + 1];
		String shortHex = StringUtils.toHexString(shortest);
		// 2^15 zero bytes, twice
		setContents("0C" + "00008000" + shortHex +
				"0C" + "00008000" + shortHex);
		r.skipBytes(Integer.MAX_VALUE);
		r.skipBytes(Integer.MAX_VALUE);
		assertTrue(r.eof());
	}

	@Test
	public void testSkipBytes32ChecksMaxLength() throws Exception {
		byte[] shortest = new byte[Short.MAX_VALUE + 1];
		String shortHex = StringUtils.toHexString(shortest);
		// 2^15 zero bytes, twice
		setContents("0C" + "00008000" + shortHex +
				"0C" + "00008000" + shortHex);
		r.skipBytes(Short.MAX_VALUE + 1);
		assertTrue(r.hasBytes());
		try {
			r.skipBytes(Short.MAX_VALUE);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testBytesMustHaveMinimalLength() throws Exception {
		// BYTES_16 could be encoded as BYTES_8
		byte[] longest8 = new byte[Byte.MAX_VALUE];
		String long8Hex = StringUtils.toHexString(longest8);
		setContents("0A" + "7F" + long8Hex + "0B" + "007F" + long8Hex);
		assertArrayEquals(longest8, r.readBytes(Integer.MAX_VALUE));
		try {
			r.readBytes(Integer.MAX_VALUE);
			fail();
		} catch(FormatException expected) {}
		// BYTES_32 could be encoded as BYTES_16
		byte[] longest16 = new byte[Short.MAX_VALUE];
		String long16Hex = StringUtils.toHexString(longest16);
		setContents("0B" + "7FFF" + long16Hex + "0C" + "00007FFF" + long16Hex);
		assertArrayEquals(longest16, r.readBytes(Integer.MAX_VALUE));
		try {
			r.readBytes(Integer.MAX_VALUE);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testReadList() throws Exception {
		// A list containing 1, "foo", and 128
		setContents("0D" + "02" + "01" +
				"07" + "03" + "666F6F" +
				"03" + "0080" + "10");
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
		setContents("0D" + "02" + "01" +
				"07" + "03" + "666F6F" +
				"03" + "0080" + "10");
		r.skipList();
		assertTrue(r.eof());
	}

	@Test
	public void testReadMap() throws Exception {
		// A map containing "foo" -> 123 and byte[0] -> null
		setContents("0E" + "07" + "03" + "666F6F" + "02" + "7B" +
				"0A" + "00" + "11" + "10");
		r.readMapStart();
		assertFalse(r.hasMapEnd());
		assertEquals("foo", r.readString(1000));
		assertFalse(r.hasMapEnd());
		assertEquals(123, r.readInteger());
		assertFalse(r.hasMapEnd());
		assertArrayEquals(new byte[0], r.readBytes(1000));
		assertFalse(r.hasMapEnd());
		assertTrue(r.hasNull());
		r.readNull();
		assertTrue(r.hasMapEnd());
		r.readMapEnd();
		assertTrue(r.eof());
	}

	@Test
	public void testSkipMap() throws Exception {
		// A map containing "foo" -> 123 and byte[0] -> null
		setContents("0E" + "07" + "03" + "666F6F" + "02" + "7B" +
				"0A" + "00" + "11" + "10");
		r.skipMap();
		assertTrue(r.eof());
	}

	@Test
	public void testReadStruct() throws Exception {
		// Two empty structs with IDs 0 and 255
		setContents("0F00" + "10" + "0FFF" + "10");
		r.readStructStart(0);
		r.readStructEnd();
		r.readStructStart(255);
		r.readStructEnd();
		assertTrue(r.eof());
	}

	@Test
	public void testSkipStruct() throws Exception {
		// Two empty structs with IDs 0 and 255
		setContents("0F00" + "10" + "0FFF" + "10");
		r.skipStruct();
		r.skipStruct();
		assertTrue(r.eof());
	}

	@Test
	public void testSkipNestedStructMapAndList() throws Exception {
		// A struct containing a map containing two empty lists
		setContents("0F00" + "0E" + "0D" + "10" + "0D" + "10" + "10" + "10");
		r.skipStruct();
		assertTrue(r.eof());
	}

	@Test
	public void testReadNull() throws Exception {
		setContents("11");
		r.readNull();
		assertTrue(r.eof());
	}

	@Test
	public void testSkipNull() throws Exception {
		setContents("11");
		r.skipNull();
		assertTrue(r.eof());
	}

	@Test
	public void testReadEmptyInput() throws Exception {
		setContents("");
		assertTrue(r.eof());
	}

	private void setContents(String hex) {
		in = new ByteArrayInputStream(StringUtils.fromHexString(hex));
		r = new ReaderImpl(in);
	}
}
