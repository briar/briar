package org.briarproject.serial;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.briarproject.BriarTestCase;
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
		w.writeInteger(Long.MIN_VALUE);
		w.writeInteger(Long.MAX_VALUE);
		// INTEGER tag, 0, INTEGER tag, -1, etc
		checkContents("02" + "0000000000000000" + "02" + "FFFFFFFFFFFFFFFF"
				+ "02" + "8000000000000000" + "02" + "7FFFFFFFFFFFFFFF");
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
		checkContents("03" + "0000000000000000" + "03" + "3FF0000000000000"
				+ "03" + "4000000000000000" + "03" + "BFF0000000000000"
				+ "03" + "8000000000000000" + "03" + "FFF0000000000000"
				+ "03" + "7FF0000000000000" + "03" + "7FF8000000000000");
	}

	@Test
	public void testWriteString() throws IOException {
		w.writeString("foo bar baz bam ");
		// STRING tag, length 16, UTF-8 bytes
		checkContents("04" + "00000010" + "666F6F206261722062617A2062616D20");
	}

	@Test
	public void testWriteBytes() throws IOException {
		w.writeBytes(new byte[] {
				0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
		});
		// BYTES tag, length 16, bytes
		checkContents("05" + "00000010" + "000102030405060708090A0B0C0D0E0F");
	}

	@Test
	public void testWriteList() throws IOException {
		List<Object> l = new ArrayList<Object>();
		for(int i = 0; i < 3; i++) l.add(i);
		w.writeList(l);
		// LIST tag, elements as integers, END tag
		checkContents("06" + "02" + "0000000000000000" +
				"02" + "0000000000000001" + "02" + "0000000000000002" + "09");
	}

	@Test
	public void testListCanContainNull() throws IOException {
		List<Object> l = new ArrayList<Object>();
		l.add(1);
		l.add(null);
		l.add(2);
		w.writeList(l);
		// LIST tag, 1 as integer, NULL tag, 2 as integer, END tag
		checkContents("06" + "02" + "0000000000000001" + "0A" +
				"02" + "0000000000000002" + "09");
	}

	@Test
	public void testWriteMap() throws IOException {
		// Use LinkedHashMap to get predictable iteration order
		Map<Object, Object> m = new LinkedHashMap<Object, Object>();
		for(int i = 0; i < 4; i++) m.put(i, i + 1);
		w.writeMap(m);
		// MAP tag, entries as integers, END tag
		checkContents("07" + "02" + "0000000000000000" +
				"02" + "0000000000000001" + "02" + "0000000000000001" +
				"02" + "0000000000000002" + "02" + "0000000000000002" +
				"02" + "0000000000000003" + "02" + "0000000000000003" +
				"02" + "0000000000000004" + "09");
	}

	@Test
	public void testWriteDelimitedList() throws IOException {
		w.writeListStart();
		w.writeInteger(1);
		w.writeString("foo");
		w.writeInteger(128);
		w.writeListEnd();
		// LIST tag, 1 as integer, "foo" as string, 128 as integer, END tag
		checkContents("06" + "02" + "0000000000000001" +
				"04" + "00000003" + "666F6F" +
				"02" + "0000000000000080" + "09");
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
		checkContents("07" + "04" + "00000003" + "666F6F" +
				"02" + "000000000000007B" + "05" + "00000000" + "0A" + "09");
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
		checkContents("07" + "07" + "04" + "00000003" + "666F6F" +
				"02" + "000000000000007B" + "09" + "06" +
				"02" + "0000000000000001" + "09" + "09");
	}

	@Test
	public void testWriteStruct() throws IOException {
		w.writeStructStart(123);
		w.writeStructEnd();
		// STRUCT tag, 123 as struct ID, END tag
		checkContents("08" + "7B" + "09");
	}

	@Test
	public void testWriteNull() throws IOException {
		w.writeNull();
		checkContents("0A");
	}

	private void checkContents(String hex) throws IOException {
		out.flush();
		out.close();
		byte[] expected = StringUtils.fromHexString(hex);
		assertTrue(StringUtils.toHexString(out.toByteArray()),
				Arrays.equals(expected, out.toByteArray()));
	}
}
