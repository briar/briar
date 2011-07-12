package net.sf.briar.serial;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;
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
		checkContents("FEFF");
	}

	@Test
	public void testWriteUint7() throws IOException {
		w.writeUint7((byte) 0);
		w.writeUint7((byte) 127);
		checkContents("00" + "7F");
	}

	@Test
	public void testWriteInt8() throws IOException {
		w.writeInt8((byte) 0);
		w.writeInt8((byte) -1);
		w.writeInt8((byte) -128);
		w.writeInt8((byte) 127);
		checkContents("FD00" + "FDFF" + "FD80" + "FD7F");
	}

	@Test
	public void testWriteInt16() throws IOException {
		w.writeInt16((short) 0);
		w.writeInt16((short) -1);
		w.writeInt16((short) -32768);
		w.writeInt16((short) 32767);
		checkContents("FC0000" + "FCFFFF" + "FC8000" + "FC7FFF");
	}

	@Test
	public void testWriteInt32() throws IOException {
		w.writeInt32(0);
		w.writeInt32(-1);
		w.writeInt32(-2147483648);
		w.writeInt32(2147483647);
		checkContents("FB00000000" + "FBFFFFFFFF" +
				"FB80000000" + "FB7FFFFFFF");
	}

	@Test
	public void testWriteInt64() throws IOException {
		w.writeInt64(0L);
		w.writeInt64(-1L);
		w.writeInt64(-9223372036854775808L);
		w.writeInt64(9223372036854775807L);
		checkContents("FA0000000000000000" + "FAFFFFFFFFFFFFFFFF" +
				"FA8000000000000000" + "FA7FFFFFFFFFFFFFFF");
	}

	@Test
	public void testWriteIntAny() throws IOException {
		w.writeIntAny(0L); // uint7
		w.writeIntAny(127L); // uint7
		w.writeIntAny(-1L); // int8
		w.writeIntAny(128L); // int16
		w.writeIntAny(32767L); // int16
		w.writeIntAny(32768L); // int32
		w.writeIntAny(2147483647L); // int32
		w.writeIntAny(2147483648L); // int64
		checkContents("00" + "7F" + "FDFF" + "FC0080" + "FC7FFF" +
				"FB00008000" + "FB7FFFFFFF" + "FA0000000080000000");
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
		checkContents("F900000000" + "F93F800000" + "F940000000" +
				"F9BF800000" + "F980000000" + "F9FF800000" +
				"F97F800000" + "F97FC00000");
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
		checkContents("F80000000000000000" + "F83FF0000000000000" +
				"F84000000000000000" + "F8BFF0000000000000" +
				"F88000000000000000" + "F8FFF0000000000000" +
				"F87FF0000000000000" + "F87FF8000000000000");
	}

	@Test
	public void testWriteUtf8() throws IOException {
		w.writeUtf8("foo");
		// UTF-8 tag, length as uint7, UTF-8 bytes
		checkContents("F7" + "03" + "666F6F");
	}

	@Test
	public void testWriteRawBytes() throws IOException {
		w.writeRaw(new byte[] {0, 1, -1, 127, -128});
		checkContents("F6" + "05" + "0001FF7F80");
	}

	@Test
	public void testWriteRawObject() throws IOException {
		w.writeRaw(new RawImpl(new byte[] {0, 1, -1, 127, -128}));
		checkContents("F6" + "05" + "0001FF7F80");
	}

	@Test
	public void testWriteDefiniteList() throws IOException {
		List<Object> l = new ArrayList<Object>();
		l.add(Byte.valueOf((byte) 1)); // Written as a uint7
		l.add("foo");
		l.add(Long.valueOf(128L)); // Written as an int16
		w.writeList(l);
		checkContents("F5" + "03" + "01" + "F703666F6F" + "FC0080");
	}

	@Test
	public void testWriteDefiniteMap() throws IOException {
		// Use LinkedHashMap to get predictable iteration order
		Map<Object, Object> m = new LinkedHashMap<Object, Object>();
		m.put("foo", Integer.valueOf(123)); // Written as a uint7
		m.put(new RawImpl(new byte[] {}), null); // Empty array != null
		w.writeMap(m);
		checkContents("F4" + "02" + "F703666F6F" + "7B" + "F600" + "F0");
	}

	@Test
	public void testWriteIndefiniteList() throws IOException {
		w.writeListStart();
		w.writeIntAny((byte) 1); // Written as uint7
		w.writeUtf8("foo");
		w.writeIntAny(128L); // Written as an int16
		w.writeListEnd();
		checkContents("F3" + "01" + "F703666F6F" + "FC0080" + "F1");
	}

	@Test
	public void testWriteIndefiniteMap() throws IOException {
		w.writeMapStart();
		w.writeUtf8("foo");
		w.writeIntAny(123); // Written as a uint7
		w.writeRaw(new byte[] {});
		w.writeNull();
		w.writeMapEnd();
		checkContents("F2" + "F703666F6F" + "7B" + "F600" + "F0" + "F1");
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
		checkContents("F4" + "01" + "F4" + "01" + "F703666F6F" + "7B" +
				"F5" + "01" + "01");
	}

	@Test
	public void testWriteNull() throws IOException {
		w.writeNull();
		checkContents("F0");
	}

	private void checkContents(String hex) throws IOException {
		out.flush();
		out.close();
		byte[] expected = StringUtils.fromHexString(hex);
		assertTrue(StringUtils.toHexString(out.toByteArray()),
				Arrays.equals(expected, out.toByteArray()));
		assertEquals(expected.length, w.getRawBytesWritten());
	}
}
