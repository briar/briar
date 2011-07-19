package net.sf.briar.serial;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;
import net.sf.briar.api.serial.RawByteArray;
import net.sf.briar.api.serial.Writable;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.util.StringUtils;

import org.junit.Before;
import org.junit.Test;

public class WriterImplTest extends TestCase {

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
		checkContents("FE" + "FF");
	}

	@Test
	public void testWriteUint7() throws IOException {
		w.writeUint7((byte) 0);
		w.writeUint7(Byte.MAX_VALUE);
		// 0, 127
		checkContents("00" + "7F");
	}

	@Test
	public void testWriteInt8() throws IOException {
		w.writeInt8((byte) 0);
		w.writeInt8((byte) -1);
		w.writeInt8(Byte.MIN_VALUE);
		w.writeInt8(Byte.MAX_VALUE);
		// INT8 tag, 0, INT8 tag, -1, INT8 tag, -128, INT8 tag, 127
		checkContents("FD" + "00" + "FD" + "FF" + "FD" + "80" + "FD" + "7F");
	}

	@Test
	public void testWriteInt16() throws IOException {
		w.writeInt16((short) 0);
		w.writeInt16((short) -1);
		w.writeInt16(Short.MIN_VALUE);
		w.writeInt16(Short.MAX_VALUE);
		// INT16 tag, 0, INT16 tag, -1, INT16 tag, -32768, INT16 tag, 32767
		checkContents("FC" + "0000" + "FC" + "FFFF" + "FC" + "8000"
				+ "FC" + "7FFF");
	}

	@Test
	public void testWriteInt32() throws IOException {
		w.writeInt32(0);
		w.writeInt32(-1);
		w.writeInt32(Integer.MIN_VALUE);
		w.writeInt32(Integer.MAX_VALUE);
		// INT32 tag, 0, INT32 tag, -1, etc
		checkContents("FB" + "00000000" + "FB" + "FFFFFFFF" + "FB" + "80000000"
				+ "FB" + "7FFFFFFF");
	}

	@Test
	public void testWriteInt64() throws IOException {
		w.writeInt64(0L);
		w.writeInt64(-1L);
		w.writeInt64(Long.MIN_VALUE);
		w.writeInt64(Long.MAX_VALUE);
		// INT64 tag, 0, INT64 tag, -1, etc
		checkContents("FA" + "0000000000000000" + "FA" + "FFFFFFFFFFFFFFFF"
				+ "FA" + "8000000000000000" + "FA" + "7FFFFFFFFFFFFFFF");
	}

	@Test
	public void testWriteIntAny() throws IOException {
		w.writeIntAny(0); // uint7
		w.writeIntAny(-1); // int8
		w.writeIntAny(Byte.MAX_VALUE); // uint7
		w.writeIntAny(Byte.MAX_VALUE + 1); // int16
		w.writeIntAny(Short.MAX_VALUE); // int16
		w.writeIntAny(Short.MAX_VALUE + 1); // int32
		w.writeIntAny(Integer.MAX_VALUE); // int32
		w.writeIntAny(Integer.MAX_VALUE + 1L); // int64
		checkContents("00" + "FDFF" + "7F" + "FC0080" + "FC7FFF"
				+ "FB00008000" + "FB7FFFFFFF" + "FA0000000080000000");
	}

	@Test
	public void testWriteFloat32() throws IOException {
		// http://babbage.cs.qc.edu/IEEE-754/Decimal.html
		// 1 bit for sign, 8 for exponent, 23 for significand 
		w.writeFloat32(0F); // 0 0 0 -> 0x00000000
		w.writeFloat32(1F); // 0 127 1 -> 0x3F800000
		w.writeFloat32(2F); // 0 128 1 -> 0x40000000
		w.writeFloat32(-1F); // 1 127 1 -> 0xBF800000
		w.writeFloat32(-0F); // 1 0 0 -> 0x80000000
		// http://steve.hollasch.net/cgindex/coding/ieeefloat.html
		w.writeFloat32(Float.NEGATIVE_INFINITY); // 1 255 0 -> 0xFF800000
		w.writeFloat32(Float.POSITIVE_INFINITY); // 0 255 0 -> 0x7F800000
		w.writeFloat32(Float.NaN); // 0 255 1 -> 0x7FC00000
		checkContents("F9" + "00000000" + "F9" + "3F800000" + "F9" + "40000000"
				+ "F9" + "BF800000" + "F9" + "80000000" + "F9" + "FF800000"
				+ "F9" + "7F800000" + "F9" + "7FC00000");
	}

	@Test
	public void testWriteFloat64() throws IOException {
		// 1 bit for sign, 11 for exponent, 52 for significand 
		w.writeFloat64(0.0); // 0 0 0 -> 0x0000000000000000
		w.writeFloat64(1.0); // 0 1023 1 -> 0x3FF0000000000000
		w.writeFloat64(2.0); // 0 1024 1 -> 0x4000000000000000
		w.writeFloat64(-1.0); // 1 1023 1 -> 0xBFF0000000000000
		w.writeFloat64(-0.0); // 1 0 0 -> 0x8000000000000000
		w.writeFloat64(Double.NEGATIVE_INFINITY); // 1 2047 0 -> 0xFFF00000...
		w.writeFloat64(Double.POSITIVE_INFINITY); // 0 2047 0 -> 0x7FF00000...
		w.writeFloat64(Double.NaN); // 0 2047 1 -> 0x7FF8000000000000
		checkContents("F8" + "0000000000000000" + "F8" + "3FF0000000000000"
				+ "F8" + "4000000000000000" + "F8" + "BFF0000000000000"
				+ "F8" + "8000000000000000" + "F8" + "FFF0000000000000"
				+ "F8" + "7FF0000000000000" + "F8" + "7FF8000000000000");
	}

	@Test
	public void testWriteShortString() throws IOException {
		w.writeString("foo bar baz bam");
		// SHORT_STRING tag, length 15, UTF-8 bytes
		checkContents("8" + "F" + "666F6F206261722062617A2062616D");
	}

	@Test
	public void testWriteString() throws IOException {
		w.writeString("foo bar baz bam ");
		// STRING tag, length 16 as uint7, UTF-8 bytes
		checkContents("F7" + "10" + "666F6F206261722062617A2062616D20");
	}

	@Test
	public void testWriteShortRawBytes() throws IOException {
		w.writeRaw(new byte[] {
				0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14
		});
		// SHORT_RAW tag, length 15, raw bytes
		checkContents("9" + "F" + "000102030405060708090A0B0C0D0E");
	}

	@Test
	public void testWriteShortRawObject() throws IOException {
		w.writeRaw(new RawByteArray(new byte[] {
				0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14
		}));
		// SHORT_RAW tag, length 15, raw bytes
		checkContents("9" + "F" + "000102030405060708090A0B0C0D0E");
	}

	@Test
	public void testWriteRawBytes() throws IOException {
		w.writeRaw(new byte[] {
				0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
		});
		// RAW tag, length 16 as uint7, raw bytes
		checkContents("F6" + "10" + "000102030405060708090A0B0C0D0E0F");
	}

	@Test
	public void testWriteRawObject() throws IOException {
		w.writeRaw(new RawByteArray(new byte[] {
				0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
		}));
		// RAW tag, length 16 as uint7, raw bytes
		checkContents("F6" + "10" + "000102030405060708090A0B0C0D0E0F");
	}

	@Test
	public void testWriteShortList() throws IOException {
		List<Object> l = new ArrayList<Object>();
		for(int i = 0; i < 15; i++) l.add(i);
		w.writeList(l);
		// SHORT_LIST tag, length, elements as uint7
		checkContents("A" + "F" + "000102030405060708090A0B0C0D0E");
	}

	@Test
	public void testWriteList() throws IOException {
		List<Object> l = new ArrayList<Object>();
		for(int i = 0; i < 16; i++) l.add(i);
		w.writeList(l);
		// LIST tag, length as uint7, elements as uint7
		checkContents("F5" + "10" + "000102030405060708090A0B0C0D0E0F");
	}

	@Test
	public void testListCanContainNull() throws IOException {
		List<Object> l = new ArrayList<Object>();
		l.add(1);
		l.add(null);
		l.add(2);
		w.writeList(l);
		// SHORT_LIST tag, length, 1 as uint7, null, 2 as uint7
		checkContents("A" + "3" + "01" + "F0" + "02");
	}

	@Test
	public void testWriteShortMap() throws IOException {
		// Use LinkedHashMap to get predictable iteration order
		Map<Object, Object> m = new LinkedHashMap<Object, Object>();
		for(int i = 0; i < 15; i++) m.put(i, i + 1);
		w.writeMap(m);
		// SHORT_MAP tag, size, entries as uint7
		checkContents("B" + "F" + "0001" + "0102" + "0203" + "0304" + "0405"
				+ "0506" + "0607" + "0708" + "0809" + "090A" + "0A0B" + "0B0C"
				+ "0C0D" + "0D0E" + "0E0F");
	}

	@Test
	public void testWriteMap() throws IOException {
		// Use LinkedHashMap to get predictable iteration order
		Map<Object, Object> m = new LinkedHashMap<Object, Object>();
		for(int i = 0; i < 16; i++) m.put(i, i + 1);
		w.writeMap(m);
		// MAP tag, size as uint7, entries as uint7
		checkContents("F4" + "10" + "0001" + "0102" + "0203" + "0304" + "0405"
				+ "0506" + "0607" + "0708" + "0809" + "090A" + "0A0B" + "0B0C"
				+ "0C0D" + "0D0E" + "0E0F" + "0F10");
	}

	@Test
	public void testWriteDelimitedList() throws IOException {
		w.writeListStart();
		w.writeIntAny((byte) 1); // Written as uint7
		w.writeString("foo"); // Written as short string
		w.writeIntAny(128L); // Written as an int16
		w.writeListEnd();
		// LIST_START tag, 1 as uint7, "foo" as short string, 128 as int16,
		// END tag
		checkContents("F3" + "01" + "83666F6F" + "FC0080" + "F1");
	}

	@Test
	public void testWriteDelimitedMap() throws IOException {
		w.writeMapStart();
		w.writeString("foo"); // Written as short string
		w.writeIntAny(123); // Written as a uint7
		w.writeRaw(new byte[] {}); // Written as short raw
		w.writeNull();
		w.writeMapEnd();
		// MAP_START tag, "foo" as short string, 123 as uint7,
		// byte[] {} as short raw, NULL tag, END tag
		checkContents("F2" + "83666F6F" + "7B" + "90" + "F0" + "F1");
	}

	@Test
	public void testWriteNestedMapsAndLists() throws IOException {
		Map<Object, Object> m = new LinkedHashMap<Object, Object>();
		m.put("foo", Integer.valueOf(123));
		List<Object> l = new ArrayList<Object>();
		l.add(Byte.valueOf((byte) 1));
		Map<Object, Object> m1 = new LinkedHashMap<Object, Object>();
		m1.put(m, l);
		w.writeMap(m1);
		// SHORT_MAP tag, length 1, SHORT_MAP tag, length 1,
		// "foo" as short string, 123 as uint7, SHORT_LIST tag, length 1,
		// 1 as uint7
		checkContents("B" + "1" + "B" + "1" + "83666F6F" + "7B" + "A1" + "01");
	}

	@Test
	public void testWriteNull() throws IOException {
		w.writeNull();
		checkContents("F0");
	}

	@Test
	public void testWriteShortUserDefinedTag() throws IOException {
		w.writeUserDefinedTag(0);
		w.writeUserDefinedTag(31);
		// SHORT_USER tag (3 bits), 0 (5 bits), SHORT_USER tag (3 bits),
		// 31 (5 bits)
		checkContents("C0" + "DF");
	}

	@Test
	public void testWriteUserDefinedTag() throws IOException {
		w.writeUserDefinedTag(32);
		w.writeUserDefinedTag(Integer.MAX_VALUE);
		// USER tag, 32 as uint7, USER tag, 2147483647 as int32
		checkContents("EF" + "20" + "EF" + "FB7FFFFFFF");
	}

	@Test
	public void testWriteCollectionOfWritables() throws IOException {
		Writable writable = new Writable() {
			public void writeTo(Writer w) throws IOException {
				w.writeUserDefinedTag(0);
				w.writeString("foo");
			}
		};
		w.writeList(Collections.singleton(writable));
		// SHORT_LIST tag, length 1, SHORT_USER tag (3 bits), 0 (5 bits),
		// "foo" as short string
		checkContents("A" + "1" + "C0" + "83666F6F");
	}

	private void checkContents(String hex) throws IOException {
		out.flush();
		out.close();
		byte[] expected = StringUtils.fromHexString(hex);
		assertTrue(StringUtils.toHexString(out.toByteArray()),
				Arrays.equals(expected, out.toByteArray()));
		assertEquals(expected.length, w.getBytesWritten());
	}
}
