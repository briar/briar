package net.sf.briar.serial;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import junit.framework.TestCase;
import net.sf.briar.api.serial.Raw;
import net.sf.briar.api.serial.RawByteArray;
import net.sf.briar.util.StringUtils;

import org.junit.Test;

public class ReaderImplTest extends TestCase {

	private ByteArrayInputStream in = null;
	private ReaderImpl r = null;

	@Test
	public void testReadBoolean() throws IOException {
		setContents("FFFE");
		assertFalse(r.readBoolean());
		assertTrue(r.readBoolean());
		assertTrue(r.eof());
	}

	@Test
	public void testReadInt8() throws IOException {
		setContents("FD00" + "FDFF" + "FD7F" + "FD80");
		assertEquals((byte) 0, r.readInt8());
		assertEquals((byte) -1, r.readInt8());
		assertEquals(Byte.MAX_VALUE, r.readInt8());
		assertEquals(Byte.MIN_VALUE, r.readInt8());
		assertTrue(r.eof());
	}

	@Test
	public void testReadInt16() throws IOException {
		setContents("FC0000" + "FCFFFF" + "FC7FFF" + "FC8000");
		assertEquals((short) 0, r.readInt16());
		assertEquals((short) -1, r.readInt16());
		assertEquals(Short.MAX_VALUE, r.readInt16());
		assertEquals(Short.MIN_VALUE, r.readInt16());
		assertTrue(r.eof());
	}

	@Test
	public void testReadInt32() throws IOException {
		setContents("FB00000000" + "FBFFFFFFFF" + "FB7FFFFFFF" + "FB80000000");
		assertEquals(0, r.readInt32());
		assertEquals(-1, r.readInt32());
		assertEquals(Integer.MAX_VALUE, r.readInt32());
		assertEquals(Integer.MIN_VALUE, r.readInt32());
		assertTrue(r.eof());
	}

	@Test
	public void testReadInt64() throws IOException {
		setContents("FA0000000000000000" + "FAFFFFFFFFFFFFFFFF" +
				"FA7FFFFFFFFFFFFFFF" + "FA8000000000000000");
		assertEquals(0L, r.readInt64());
		assertEquals(-1L, r.readInt64());
		assertEquals(Long.MAX_VALUE, r.readInt64());
		assertEquals(Long.MIN_VALUE, r.readInt64());
		assertTrue(r.eof());
	}

	@Test
	public void testReadIntAny() throws IOException {
		setContents("00" + "7F" + "FD80" + "FDFF" + "FC0080" + "FC7FFF" +
				"FB00008000" + "FB7FFFFFFF" + "FA0000000080000000");
		assertEquals(0L, r.readIntAny());
		assertEquals(127L, r.readIntAny());
		assertEquals(-128L, r.readIntAny());
		assertEquals(-1L, r.readIntAny());
		assertEquals(128L, r.readIntAny());
		assertEquals(32767L, r.readIntAny());
		assertEquals(32768L, r.readIntAny());
		assertEquals(2147483647L, r.readIntAny());
		assertEquals(2147483648L, r.readIntAny());
		assertTrue(r.eof());
	}

	@Test
	public void testReadFloat32() throws IOException {
		// http://babbage.cs.qc.edu/IEEE-754/Decimal.html
		// http://steve.hollasch.net/cgindex/coding/ieeefloat.html
		setContents("F900000000" + "F93F800000" + "F940000000" + "F9BF800000" +
				"F980000000" + "F9FF800000" + "F97F800000" + "F97FC00000");
		assertEquals(0F, r.readFloat32());
		assertEquals(1F, r.readFloat32());
		assertEquals(2F, r.readFloat32());
		assertEquals(-1F, r.readFloat32());
		assertEquals(-0F, r.readFloat32());
		assertEquals(Float.NEGATIVE_INFINITY, r.readFloat32());
		assertEquals(Float.POSITIVE_INFINITY, r.readFloat32());
		assertTrue(Float.isNaN(r.readFloat32()));
		assertTrue(r.eof());
	}

	@Test
	public void testReadFloat64() throws IOException {
		setContents("F80000000000000000" + "F83FF0000000000000" +
				"F84000000000000000" + "F8BFF0000000000000" +
				"F88000000000000000" + "F8FFF0000000000000" +
				"F87FF0000000000000" + "F87FF8000000000000");
		assertEquals(0.0, r.readFloat64());
		assertEquals(1.0, r.readFloat64());
		assertEquals(2.0, r.readFloat64());
		assertEquals(-1.0, r.readFloat64());
		assertEquals(-0.0, r.readFloat64());
		assertEquals(Double.NEGATIVE_INFINITY, r.readFloat64());
		assertEquals(Double.POSITIVE_INFINITY, r.readFloat64());
		assertTrue(Double.isNaN(r.readFloat64()));
		assertTrue(r.eof());
	}

	@Test
	public void testReadString() throws IOException {
		setContents("F703666F6F" + "83666F6F" + "F700" + "80");
		assertEquals("foo", r.readString());
		assertEquals("foo", r.readString());
		assertEquals("", r.readString());
		assertEquals("", r.readString());
		assertTrue(r.eof());
	}

	@Test
	public void testReadRaw() throws IOException {
		setContents("F603010203" + "93010203" + "F600" + "90");
		assertTrue(Arrays.equals(new byte[] {1, 2, 3}, r.readRaw()));
		assertTrue(Arrays.equals(new byte[] {1, 2, 3}, r.readRaw()));
		assertTrue(Arrays.equals(new byte[] {}, r.readRaw()));
		assertTrue(Arrays.equals(new byte[] {}, r.readRaw()));
		assertTrue(r.eof());
	}

	@Test
	public void testReadShortList() throws IOException {
		setContents("A" + "3" + "01" + "83666F6F" + "FC0080");
		List<Object> l = r.readList(Object.class);
		assertNotNull(l);
		assertEquals(3, l.size());
		assertEquals((byte) 1, l.get(0));
		assertEquals("foo", l.get(1));
		assertEquals((short) 128, l.get(2));
		assertTrue(r.eof());
	}

	@Test
	public void testReadList() throws IOException {
		setContents("F5" + "03" + "01" + "83666F6F" + "FC0080");
		List<Object> l = r.readList(Object.class);
		assertNotNull(l);
		assertEquals(3, l.size());
		assertEquals((byte) 1, l.get(0));
		assertEquals("foo", l.get(1));
		assertEquals((short) 128, l.get(2));
		assertTrue(r.eof());
	}

	@Test
	public void testReadListTypeSafe() throws IOException {
		setContents("A" + "3" + "01" + "02" + "03");
		List<Byte> l = r.readList(Byte.class);
		assertNotNull(l);
		assertEquals(3, l.size());
		assertEquals(Byte.valueOf((byte) 1), l.get(0));
		assertEquals(Byte.valueOf((byte) 2), l.get(1));
		assertEquals(Byte.valueOf((byte) 3), l.get(2));
		assertTrue(r.eof());
	}

	@Test
	public void testReadShortMap() throws IOException {
		setContents("B" + "2" + "83666F6F" + "7B" + "90" + "F0");
		Map<Object, Object> m = r.readMap(Object.class, Object.class);
		assertNotNull(m);
		assertEquals(2, m.size());
		assertEquals((byte) 123, m.get("foo"));
		Raw raw = new RawByteArray(new byte[] {});
		assertTrue(m.containsKey(raw));
		assertNull(m.get(raw));
		assertTrue(r.eof());
	}

	@Test
	public void testReadMap() throws IOException {
		setContents("F4" + "02" + "83666F6F" + "7B" + "90" + "F0");
		Map<Object, Object> m = r.readMap(Object.class, Object.class);
		assertNotNull(m);
		assertEquals(2, m.size());
		assertEquals((byte) 123, m.get("foo"));
		Raw raw = new RawByteArray(new byte[] {});
		assertTrue(m.containsKey(raw));
		assertNull(m.get(raw));
		assertTrue(r.eof());
	}

	@Test
	public void testReadMapTypeSafe() throws IOException {
		setContents("B" + "2" + "83666F6F" + "7B" + "80" + "F0");
		Map<String, Byte> m = r.readMap(String.class, Byte.class);
		assertNotNull(m);
		assertEquals(2, m.size());
		assertEquals(Byte.valueOf((byte) 123), m.get("foo"));
		assertTrue(m.containsKey(""));
		assertNull(m.get(""));
		assertTrue(r.eof());
	}

	@Test
	public void testReadDelimitedList() throws IOException {
		setContents("F3" + "01" + "83666F6F" + "FC0080" + "F1");
		List<Object> l = r.readList(Object.class);
		assertNotNull(l);
		assertEquals(3, l.size());
		assertEquals((byte) 1, l.get(0));
		assertEquals("foo", l.get(1));
		assertEquals((short) 128, l.get(2));
		assertTrue(r.eof());
	}

	@Test
	public void testReadDelimitedListElements() throws IOException {
		setContents("F3" + "01" + "83666F6F" + "FC0080" + "F1");
		assertTrue(r.hasListStart());
		r.readListStart();
		assertFalse(r.hasListEnd());
		assertEquals((byte) 1, r.readIntAny());
		assertFalse(r.hasListEnd());
		assertEquals("foo", r.readString());
		assertFalse(r.hasListEnd());
		assertEquals((short) 128, r.readIntAny());
		assertTrue(r.hasListEnd());
		r.readListEnd();
		assertTrue(r.eof());
	}

	@Test
	public void testReadDelimitedListTypeSafe() throws IOException {
		setContents("F3" + "01" + "02" + "03" + "F1");
		List<Byte> l = r.readList(Byte.class);
		assertNotNull(l);
		assertEquals(3, l.size());
		assertEquals(Byte.valueOf((byte) 1), l.get(0));
		assertEquals(Byte.valueOf((byte) 2), l.get(1));
		assertEquals(Byte.valueOf((byte) 3), l.get(2));
		assertTrue(r.eof());
	}

	@Test
	public void testReadDelimitedMap() throws IOException {
		setContents("F2" + "83666F6F" + "7B" + "90" + "F0" + "F1");
		Map<Object, Object> m = r.readMap(Object.class, Object.class);
		assertNotNull(m);
		assertEquals(2, m.size());
		assertEquals((byte) 123, m.get("foo"));
		Raw raw = new RawByteArray(new byte[] {});
		assertTrue(m.containsKey(raw));
		assertNull(m.get(raw));
		assertTrue(r.eof());
	}

	@Test
	public void testReadDelimitedMapEntries() throws IOException {
		setContents("F2" + "83666F6F" + "7B" + "90" + "F0" + "F1");
		assertTrue(r.hasMapStart());
		r.readMapStart();
		assertFalse(r.hasMapEnd());
		assertEquals("foo", r.readString());
		assertFalse(r.hasMapEnd());
		assertEquals((byte) 123, r.readIntAny());
		assertFalse(r.hasMapEnd());
		assertTrue(Arrays.equals(new byte[] {}, r.readRaw()));
		assertFalse(r.hasMapEnd());
		assertTrue(r.hasNull());
		r.readNull();
		assertTrue(r.hasMapEnd());
		r.readMapEnd();
		assertTrue(r.eof());
	}

	@Test
	public void testReadDelimitedMapTypeSafe() throws IOException {
		setContents("F2" + "83666F6F" + "7B" + "80" + "F0" + "F1");
		Map<String, Byte> m = r.readMap(String.class, Byte.class);
		assertNotNull(m);
		assertEquals(2, m.size());
		assertEquals(Byte.valueOf((byte) 123), m.get("foo"));
		assertTrue(m.containsKey(""));
		assertNull(m.get(""));
		assertTrue(r.eof());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testReadNestedMapsAndLists() throws IOException {
		setContents("B" + "1" + "B" + "1" + "83666F6F" + "7B" +
				"A" + "1" + "01");
		Map<Object, Object> m = r.readMap(Object.class, Object.class);
		assertNotNull(m);
		assertEquals(1, m.size());
		Entry<Object, Object> e = m.entrySet().iterator().next();
		Map<Object, Object> m1 = (Map<Object, Object>) e.getKey();
		assertNotNull(m1);
		assertEquals(1, m1.size());
		assertEquals((byte) 123, m1.get("foo"));
		List<Object> l = (List<Object>) e.getValue();
		assertNotNull(l);
		assertEquals(1, l.size());
		assertEquals((byte) 1, l.get(0));
		assertTrue(r.eof());
	}

	@Test
	public void testReadUserDefinedTag() throws IOException {
		setContents("C0" + "DF" + "E0" + "20" + "E0" + "FB7FFFFFFF");
		assertEquals(0, r.readUserDefinedTag());
		assertEquals(31, r.readUserDefinedTag());
		assertEquals(32, r.readUserDefinedTag());
		assertEquals(Integer.MAX_VALUE, r.readUserDefinedTag());
		assertTrue(r.eof());
	}

	@Test
	public void testReadEmptyInput() throws IOException {
		setContents("");
		assertTrue(r.eof());
	}

	private void setContents(String hex) {
		in = new ByteArrayInputStream(StringUtils.fromHexString(hex));
		r = new ReaderImpl(in);
	}
}
