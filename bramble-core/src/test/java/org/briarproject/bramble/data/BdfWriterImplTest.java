package org.briarproject.bramble.data;

import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.bramble.util.StringUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.briarproject.bramble.api.data.BdfDictionary.NULL_VALUE;
import static org.junit.Assert.assertArrayEquals;

public class BdfWriterImplTest extends BrambleTestCase {

	private ByteArrayOutputStream out = null;
	private BdfWriterImpl w = null;

	@Before
	public void setUp() {
		out = new ByteArrayOutputStream();
		w = new BdfWriterImpl(out);
	}

	@Test
	public void testWriteNull() throws IOException {
		w.writeNull();
		checkContents("00");
	}

	@Test
	public void testWriteBoolean() throws IOException {
		w.writeBoolean(true);
		w.writeBoolean(false);
		// TRUE tag, FALSE tag
		checkContents("11" + "10");
	}

	@Test
	public void testWriteLong() throws IOException {
		w.writeLong(0);
		w.writeLong(-1);
		w.writeLong(Byte.MAX_VALUE);
		w.writeLong(Byte.MIN_VALUE);
		w.writeLong(Short.MAX_VALUE);
		w.writeLong(Short.MIN_VALUE);
		w.writeLong(Integer.MAX_VALUE);
		w.writeLong(Integer.MIN_VALUE);
		w.writeLong(Long.MAX_VALUE);
		w.writeLong(Long.MIN_VALUE);
		// INTEGER_8 tag, 0, INTEGER_8 tag, -1, etc
		checkContents("21" + "00" + "21" + "FF" +
				"21" + "7F" + "21" + "80" +
				"22" + "7FFF" + "22" + "8000" +
				"24" + "7FFFFFFF" + "24" + "80000000" +
				"28" + "7FFFFFFFFFFFFFFF" + "28" + "8000000000000000");
	}

	@Test
	public void testWriteDouble() throws IOException {
		// http://babbage.cs.qc.edu/IEEE-754/Decimal.html
		// 1 bit for sign, 11 for exponent, 52 for significand
		w.writeDouble(0.0); // 0 0 0 -> 0x0000000000000000
		w.writeDouble(1.0); // 0 1023 1 -> 0x3FF0000000000000
		w.writeDouble(2.0); // 0 1024 1 -> 0x4000000000000000
		w.writeDouble(-1.0); // 1 1023 1 -> 0xBFF0000000000000
		w.writeDouble(-0.0); // 1 0 0 -> 0x8000000000000000
		w.writeDouble(Double.NEGATIVE_INFINITY); // 1 2047 0 -> 0xFFF00000...
		w.writeDouble(Double.POSITIVE_INFINITY); // 0 2047 0 -> 0x7FF00000...
		w.writeDouble(Double.NaN); // 0 2047 1 -> 0x7FF8000000000000
		checkContents("38" + "0000000000000000" + "38" + "3FF0000000000000"
				+ "38" + "4000000000000000" + "38" + "BFF0000000000000"
				+ "38" + "8000000000000000" + "38" + "FFF0000000000000"
				+ "38" + "7FF0000000000000" + "38" + "7FF8000000000000");
	}

	@Test
	public void testWriteString8() throws IOException {
		String longest = TestUtils.getRandomString(Byte.MAX_VALUE);
		String longHex = StringUtils.toHexString(longest.getBytes("UTF-8"));
		w.writeString("foo bar baz bam ");
		w.writeString(longest);
		// STRING_8 tag, length 16, UTF-8 bytes, STRING_8 tag, length 127,
		// UTF-8 bytes
		checkContents("41" + "10" + "666F6F206261722062617A2062616D20" +
				"41" + "7F" + longHex);
	}

	@Test
	public void testWriteString16() throws IOException {
		String shortest = TestUtils.getRandomString(Byte.MAX_VALUE + 1);
		String shortHex = StringUtils.toHexString(shortest.getBytes("UTF-8"));
		String longest = TestUtils.getRandomString(Short.MAX_VALUE);
		String longHex = StringUtils.toHexString(longest.getBytes("UTF-8"));
		w.writeString(shortest);
		w.writeString(longest);
		// STRING_16 tag, length 128, UTF-8 bytes, STRING_16 tag,
		// length 2^15 - 1, UTF-8 bytes
		checkContents("42" + "0080" + shortHex + "42" + "7FFF" + longHex);
	}

	@Test
	public void testWriteString32() throws IOException {
		String shortest = TestUtils.getRandomString(Short.MAX_VALUE + 1);
		String shortHex = StringUtils.toHexString(shortest.getBytes("UTF-8"));
		w.writeString(shortest);
		// STRING_32 tag, length 2^15, UTF-8 bytes
		checkContents("44" + "00008000" + shortHex);
	}

	@Test
	public void testWriteUtf8String() throws IOException {
		String unicode = "\uFDD0\uFDD1\uFDD2\uFDD3";
		String hex = StringUtils.toHexString(unicode.getBytes("UTF-8"));
		w.writeString(unicode);
		// STRING_8 tag, length 12, UTF-8 bytes
		checkContents("41" + "0C" + hex);
	}

	@Test
	public void testWriteRaw8() throws IOException {
		byte[] longest = new byte[Byte.MAX_VALUE];
		String longHex = StringUtils.toHexString(longest);
		w.writeRaw(new byte[] {1, 2, 3});
		w.writeRaw(longest);
		// RAW_8 tag, length 3, bytes, RAW_8 tag, length 127, bytes
		checkContents("51" + "03" + "010203" + "51" + "7F" + longHex);
	}

	@Test
	public void testWriteRaw16() throws IOException {
		byte[] shortest = new byte[Byte.MAX_VALUE + 1];
		String shortHex = StringUtils.toHexString(shortest);
		byte[] longest = new byte[Short.MAX_VALUE];
		String longHex = StringUtils.toHexString(longest);
		w.writeRaw(shortest);
		w.writeRaw(longest);
		// RAW_16 tag, length 128, bytes, RAW_16 tag, length 2^15 - 1, bytes
		checkContents("52" + "0080" + shortHex + "52" + "7FFF" + longHex);
	}

	@Test
	public void testWriteRaw32() throws IOException {
		byte[] shortest = new byte[Short.MAX_VALUE + 1];
		String shortHex = StringUtils.toHexString(shortest);
		w.writeRaw(shortest);
		// RAW_32 tag, length 2^15, bytes
		checkContents("54" + "00008000" + shortHex);
	}

	@Test
	public void testWriteList() throws IOException {
		List<Object> l = new ArrayList<Object>();
		for (int i = 0; i < 3; i++) l.add(i);
		w.writeList(l);
		// LIST tag, elements as integers, END tag
		checkContents("60" + "21" + "00" + "21" + "01" + "21" + "02" + "80");
	}

	@Test
	public void testListCanContainNull() throws IOException {
		List<Object> l = new ArrayList<Object>();
		l.add(1);
		l.add(null);
		l.add(NULL_VALUE);
		l.add(2);
		w.writeList(l);
		// LIST tag, 1 as integer, NULL tag, NULL tag, 2 as integer, END tag
		checkContents("60" + "21" + "01" + "00" + "00" + "21" + "02" + "80");
	}

	@Test
	public void testWriteDictionary() throws IOException {
		// Use LinkedHashMap to get predictable iteration order
		Map<String, Object> m = new LinkedHashMap<String, Object>();
		for (int i = 0; i < 4; i++) m.put(String.valueOf(i), i);
		w.writeDictionary(m);
		// DICTIONARY tag, keys as strings and values as integers, END tag
		checkContents("70" + "41" + "01" + "30" + "21" + "00" +
				"41" + "01" + "31" + "21" + "01" +
				"41" + "01" + "32" + "21" + "02" +
				"41" + "01" + "33" + "21" + "03" + "80");
	}

	@Test
	public void testWriteDelimitedList() throws IOException {
		w.writeListStart();
		w.writeLong(1);
		w.writeString("foo");
		w.writeLong(128);
		w.writeListEnd();
		// LIST tag, 1 as integer, "foo" as string, 128 as integer, END tag
		checkContents("60" + "21" + "01" +
				"41" + "03" + "666F6F" +
				"22" + "0080" + "80");
	}

	@Test
	public void testWriteDelimitedDictionary() throws IOException {
		w.writeDictionaryStart();
		w.writeString("foo");
		w.writeLong(123);
		w.writeString("bar");
		w.writeNull();
		w.writeDictionaryEnd();
		// DICTIONARY tag, "foo" as string, 123 as integer, "bar" as string,
		// NULL tag, END tag
		checkContents("70" + "41" + "03" + "666F6F" +
				"21" + "7B" + "41" + "03" + "626172" + "00" + "80");
	}

	@Test
	public void testWriteNestedDictionariesAndLists() throws IOException {
		Map<String, Object> inner = new LinkedHashMap<String, Object>();
		inner.put("bar", new byte[0]);
		List<Object> list = new ArrayList<Object>();
		list.add(1);
		list.add(inner);
		Map<String, Object> outer = new LinkedHashMap<String, Object>();
		outer.put("foo", list);
		w.writeDictionary(outer);
		// DICTIONARY tag, "foo" as string, LIST tag, 1 as integer,
		// DICTIONARY tag, "bar" as string, {} as raw, END tag, END tag, END tag
		checkContents("70" + "41" + "03" + "666F6F" + "60" +
				"21" + "01" + "70" + "41" + "03" + "626172" + "51" + "00" +
				"80" + "80" + "80");
	}

	private void checkContents(String hex) throws IOException {
		out.flush();
		out.close();
		byte[] expected = StringUtils.fromHexString(hex);
		assertArrayEquals(StringUtils.toHexString(out.toByteArray()),
				expected, out.toByteArray());
	}
}
