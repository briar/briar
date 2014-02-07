package org.briarproject.serial;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.util.StringUtils;
import org.junit.Before;
import org.junit.Test;

public class WriterImplTest extends BriarTestCase {

	private ByteArrayOutputStream out = null;
	private WriterImpl w = null;

	@Before
	public void setUp() {
		out = new ByteArrayOutputStream();
		w = new WriterImpl(out);
	}

	@Test
	public void testWriteBoolean() throws IOException {
		w.writeBoolean(true);
		w.writeBoolean(false);
		// TRUE tag, FALSE tag
		checkContents("01" + "00");
	}

	@Test
	public void testWriteInteger() throws IOException {
		w.writeInteger(0);
		w.writeInteger(-1);
		w.writeInteger(Byte.MAX_VALUE);
		w.writeInteger(Byte.MIN_VALUE);
		w.writeInteger(Short.MAX_VALUE);
		w.writeInteger(Short.MIN_VALUE);
		w.writeInteger(Integer.MAX_VALUE);
		w.writeInteger(Integer.MIN_VALUE);
		w.writeInteger(Long.MAX_VALUE);
		w.writeInteger(Long.MIN_VALUE);
		// INTEGER_8 tag, 0, INTEGER_8 tag, -1, etc
		checkContents("02" + "00" + "02" + "FF" +
				"02" + "7F" + "02" + "80" +
				"03" + "7FFF" + "03" + "8000" +
				"04" + "7FFFFFFF" + "04" + "80000000" +
				"05" + "7FFFFFFFFFFFFFFF" + "05" + "8000000000000000");
	}

	@Test
	public void testWriteFloat() throws IOException {
		// http://babbage.cs.qc.edu/IEEE-754/Decimal.html
		// 1 bit for sign, 11 for exponent, 52 for significand
		w.writeFloat(0.0); // 0 0 0 -> 0x0000000000000000
		w.writeFloat(1.0); // 0 1023 1 -> 0x3FF0000000000000
		w.writeFloat(2.0); // 0 1024 1 -> 0x4000000000000000
		w.writeFloat(-1.0); // 1 1023 1 -> 0xBFF0000000000000
		w.writeFloat(-0.0); // 1 0 0 -> 0x8000000000000000
		w.writeFloat(Double.NEGATIVE_INFINITY); // 1 2047 0 -> 0xFFF00000...
		w.writeFloat(Double.POSITIVE_INFINITY); // 0 2047 0 -> 0x7FF00000...
		w.writeFloat(Double.NaN); // 0 2047 1 -> 0x7FF8000000000000
		checkContents("06" + "0000000000000000" + "06" + "3FF0000000000000"
				+ "06" + "4000000000000000" + "06" + "BFF0000000000000"
				+ "06" + "8000000000000000" + "06" + "FFF0000000000000"
				+ "06" + "7FF0000000000000" + "06" + "7FF8000000000000");
	}

	@Test
	public void testWriteString8() throws IOException {
		String longest = TestUtils.createRandomString(Byte.MAX_VALUE);
		String longHex = StringUtils.toHexString(longest.getBytes("UTF-8"));
		w.writeString("foo bar baz bam ");
		w.writeString(longest);
		// STRING_8 tag, length 16, UTF-8 bytes, STRING_8 tag, length 127,
		// UTF-8 bytes
		checkContents("07" + "10" + "666F6F206261722062617A2062616D20" +
				"07" + "7F" + longHex);
	}

	@Test
	public void testWriteString16() throws IOException {
		String shortest = TestUtils.createRandomString(Byte.MAX_VALUE + 1);
		String shortHex = StringUtils.toHexString(shortest.getBytes("UTF-8"));
		String longest = TestUtils.createRandomString(Short.MAX_VALUE);
		String longHex = StringUtils.toHexString(longest.getBytes("UTF-8"));
		w.writeString(shortest);
		w.writeString(longest);
		// STRING_16 tag, length 128, UTF-8 bytes, STRING_16 tag,
		// length 2^15 - 1, UTF-8 bytes
		checkContents("08" + "0080" + shortHex + "08" + "7FFF" + longHex);
	}

	@Test
	public void testWriteString32() throws IOException {
		String shortest = TestUtils.createRandomString(Short.MAX_VALUE + 1);
		String shortHex = StringUtils.toHexString(shortest.getBytes("UTF-8"));
		w.writeString(shortest);
		// STRING_32 tag, length 2^15, UTF-8 bytes
		checkContents("09" + "00008000" + shortHex);
	}

	@Test
	public void testWriteBytes8() throws IOException {
		byte[] longest = new byte[Byte.MAX_VALUE];
		String longHex = StringUtils.toHexString(longest);
		w.writeBytes(new byte[] {1, 2, 3});
		w.writeBytes(longest);
		// BYTES_8 tag, length 3, bytes, BYTES_8 tag, length 127, bytes
		checkContents("0A" + "03" + "010203" + "0A" + "7F" + longHex);
	}

	@Test
	public void testWriteBytes16() throws IOException {
		byte[] shortest = new byte[Byte.MAX_VALUE + 1];
		String shortHex = StringUtils.toHexString(shortest);
		byte[] longest = new byte[Short.MAX_VALUE];
		String longHex = StringUtils.toHexString(longest);
		w.writeBytes(shortest);
		w.writeBytes(longest);
		// BYTES_16 tag, length 128, bytes, BYTES_16 tag, length 2^15 - 1, bytes
		checkContents("0B" + "0080" + shortHex + "0B" + "7FFF" + longHex);
	}

	@Test
	public void testWriteBytes32() throws IOException {
		byte[] shortest = new byte[Short.MAX_VALUE + 1];
		String shortHex = StringUtils.toHexString(shortest);
		w.writeBytes(shortest);
		// BYTES_32 tag, length 2^15, bytes
		checkContents("0C" + "00008000" + shortHex);
	}

	@Test
	public void testWriteList() throws IOException {
		List<Object> l = new ArrayList<Object>();
		for(int i = 0; i < 3; i++) l.add(i);
		w.writeList(l);
		// LIST tag, elements as integers, END tag
		checkContents("0D" + "02" + "00" + "02" + "01" + "02" + "02" + "10");
	}

	@Test
	public void testListCanContainNull() throws IOException {
		List<Object> l = new ArrayList<Object>();
		l.add(1);
		l.add(null);
		l.add(2);
		w.writeList(l);
		// LIST tag, 1 as integer, NULL tag, 2 as integer, END tag
		checkContents("0D" + "02" + "01" + "11" + "02" + "02" + "10");
	}

	@Test
	public void testWriteMap() throws IOException {
		// Use LinkedHashMap to get predictable iteration order
		Map<Object, Object> m = new LinkedHashMap<Object, Object>();
		for(int i = 0; i < 4; i++) m.put(i, i + 1);
		w.writeMap(m);
		// MAP tag, entries as integers, END tag
		checkContents("0E" + "02" + "00" + "02" + "01" +
				"02" + "01" + "02" + "02" +
				"02" + "02" + "02" + "03" +
				"02" + "03" + "02" + "04" + "10");
	}

	@Test
	public void testWriteDelimitedList() throws IOException {
		w.writeListStart();
		w.writeInteger(1);
		w.writeString("foo");
		w.writeInteger(128);
		w.writeListEnd();
		// LIST tag, 1 as integer, "foo" as string, 128 as integer, END tag
		checkContents("0D" + "02" + "01" +
				"07" + "03" + "666F6F" +
				"03" + "0080" + "10");
	}

	@Test
	public void testWriteDelimitedMap() throws IOException {
		w.writeMapStart();
		w.writeString("foo");
		w.writeInteger(123);
		w.writeBytes(new byte[0]);
		w.writeNull();
		w.writeMapEnd();
		// MAP tag, "foo" as string, 123 as integer, {} as bytes, NULL tag,
		// END tag
		checkContents("0E" + "07" + "03" + "666F6F" +
				"02" + "7B" + "0A" + "00" + "11" + "10");
	}

	@Test
	public void testWriteNestedMapsAndLists() throws IOException {
		Map<Object, Object> m = new LinkedHashMap<Object, Object>();
		m.put("foo", 123);
		List<Object> l = new ArrayList<Object>();
		l.add((byte) 1);
		Map<Object, Object> m1 = new LinkedHashMap<Object, Object>();
		m1.put(m, l);
		w.writeMap(m1);
		// MAP tag, MAP tag, "foo" as string, 123 as integer, END tag,
		// LIST tag, 1 as integer, END tag, END tag
		checkContents("0E" + "0E" + "07" + "03" + "666F6F" +
				"02" + "7B" + "10" + "0D" + "02" + "01" + "10" + "10");
	}

	@Test
	public void testWriteStruct() throws IOException {
		w.writeStructStart(123);
		w.writeStructEnd();
		// STRUCT tag, 123 as struct ID, END tag
		checkContents("0F" + "7B" + "10");
	}

	@Test
	public void testWriteNull() throws IOException {
		w.writeNull();
		checkContents("11");
	}

	private void checkContents(String hex) throws IOException {
		out.flush();
		out.close();
		byte[] expected = StringUtils.fromHexString(hex);
		assertTrue(StringUtils.toHexString(out.toByteArray()),
				Arrays.equals(expected, out.toByteArray()));
	}
}
