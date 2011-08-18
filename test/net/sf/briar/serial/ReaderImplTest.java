package net.sf.briar.serial;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import junit.framework.TestCase;
import net.sf.briar.api.Bytes;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.util.StringUtils;

import org.junit.Test;

public class ReaderImplTest extends TestCase {

	private ByteArrayInputStream in = null;
	private ReaderImpl r = null;

	@Test
	public void testReadBoolean() throws Exception {
		setContents("FFFE");
		assertFalse(r.readBoolean());
		assertTrue(r.readBoolean());
		assertTrue(r.eof());
	}

	@Test
	public void testReadInt8() throws Exception {
		setContents("FD00" + "FDFF" + "FD7F" + "FD80");
		assertEquals((byte) 0, r.readInt8());
		assertEquals((byte) -1, r.readInt8());
		assertEquals(Byte.MAX_VALUE, r.readInt8());
		assertEquals(Byte.MIN_VALUE, r.readInt8());
		assertTrue(r.eof());
	}

	@Test
	public void testReadInt16() throws Exception {
		setContents("FC0000" + "FCFFFF" + "FC7FFF" + "FC8000");
		assertEquals((short) 0, r.readInt16());
		assertEquals((short) -1, r.readInt16());
		assertEquals(Short.MAX_VALUE, r.readInt16());
		assertEquals(Short.MIN_VALUE, r.readInt16());
		assertTrue(r.eof());
	}

	@Test
	public void testReadInt32() throws Exception {
		setContents("FB00000000" + "FBFFFFFFFF" + "FB7FFFFFFF" + "FB80000000");
		assertEquals(0, r.readInt32());
		assertEquals(-1, r.readInt32());
		assertEquals(Integer.MAX_VALUE, r.readInt32());
		assertEquals(Integer.MIN_VALUE, r.readInt32());
		assertTrue(r.eof());
	}

	@Test
	public void testReadInt64() throws Exception {
		setContents("FA0000000000000000" + "FAFFFFFFFFFFFFFFFF" +
				"FA7FFFFFFFFFFFFFFF" + "FA8000000000000000");
		assertEquals(0L, r.readInt64());
		assertEquals(-1L, r.readInt64());
		assertEquals(Long.MAX_VALUE, r.readInt64());
		assertEquals(Long.MIN_VALUE, r.readInt64());
		assertTrue(r.eof());
	}

	@Test
	public void testReadIntAny() throws Exception {
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
	public void testReadFloat32() throws Exception {
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
	public void testReadFloat64() throws Exception {
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
	public void testReadString() throws Exception {
		setContents("F703666F6F" + "83666F6F" + "F700" + "80");
		assertEquals("foo", r.readString());
		assertEquals("foo", r.readString());
		assertEquals("", r.readString());
		assertEquals("", r.readString());
		assertTrue(r.eof());
	}

	@Test
	public void testReadStringMaxLength() throws Exception {
		setContents("83666F6F" + "83666F6F");
		assertEquals("foo", r.readString(3));
		try {
			r.readString(2);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testReadBytes() throws Exception {
		setContents("F603010203" + "93010203" + "F600" + "90");
		assertTrue(Arrays.equals(new byte[] {1, 2, 3}, r.readBytes()));
		assertTrue(Arrays.equals(new byte[] {1, 2, 3}, r.readBytes()));
		assertTrue(Arrays.equals(new byte[] {}, r.readBytes()));
		assertTrue(Arrays.equals(new byte[] {}, r.readBytes()));
		assertTrue(r.eof());
	}

	@Test
	public void testReadBytesMaxLength() throws Exception {
		setContents("93010203" + "93010203");
		assertTrue(Arrays.equals(new byte[] {1, 2, 3}, r.readBytes(3)));
		try {
			r.readBytes(2);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testReadShortList() throws Exception {
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
	public void testReadList() throws Exception {
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
	public void testReadListTypeSafe() throws Exception {
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
	public void testReadListTypeSafeThrowsFormatException() throws Exception {
		setContents("A" + "3" + "01" + "83666F6F" + "03");
		// Trying to read a mixed list as a list of bytes should throw a
		// FormatException
		try {
			r.readList(Byte.class);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testReadShortMap() throws Exception {
		setContents("B" + "2" + "83666F6F" + "7B" + "90" + "F0");
		Map<Object, Object> m = r.readMap(Object.class, Object.class);
		assertNotNull(m);
		assertEquals(2, m.size());
		assertEquals((byte) 123, m.get("foo"));
		Bytes b = new Bytes(new byte[] {});
		assertTrue(m.containsKey(b));
		assertNull(m.get(b));
		assertTrue(r.eof());
	}

	@Test
	public void testReadMap() throws Exception {
		setContents("F4" + "02" + "83666F6F" + "7B" + "90" + "F0");
		Map<Object, Object> m = r.readMap(Object.class, Object.class);
		assertNotNull(m);
		assertEquals(2, m.size());
		assertEquals((byte) 123, m.get("foo"));
		Bytes b = new Bytes(new byte[] {});
		assertTrue(m.containsKey(b));
		assertNull(m.get(b));
		assertTrue(r.eof());
	}

	@Test
	public void testReadMapTypeSafe() throws Exception {
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
	public void testReadDelimitedList() throws Exception {
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
	public void testReadDelimitedListElements() throws Exception {
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
	public void testReadDelimitedListTypeSafe() throws Exception {
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
	public void testReadDelimitedMap() throws Exception {
		setContents("F2" + "83666F6F" + "7B" + "90" + "F0" + "F1");
		Map<Object, Object> m = r.readMap(Object.class, Object.class);
		assertNotNull(m);
		assertEquals(2, m.size());
		assertEquals((byte) 123, m.get("foo"));
		Bytes b = new Bytes(new byte[] {});
		assertTrue(m.containsKey(b));
		assertNull(m.get(b));
		assertTrue(r.eof());
	}

	@Test
	public void testReadDelimitedMapEntries() throws Exception {
		setContents("F2" + "83666F6F" + "7B" + "90" + "F0" + "F1");
		assertTrue(r.hasMapStart());
		r.readMapStart();
		assertFalse(r.hasMapEnd());
		assertEquals("foo", r.readString());
		assertFalse(r.hasMapEnd());
		assertEquals((byte) 123, r.readIntAny());
		assertFalse(r.hasMapEnd());
		assertTrue(Arrays.equals(new byte[] {}, r.readBytes()));
		assertFalse(r.hasMapEnd());
		assertTrue(r.hasNull());
		r.readNull();
		assertTrue(r.hasMapEnd());
		r.readMapEnd();
		assertTrue(r.eof());
	}

	@Test
	public void testReadDelimitedMapTypeSafe() throws Exception {
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
	public void testReadNestedMapsAndLists() throws Exception {
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
	public void testReadUserDefined() throws Exception {
		setContents("C0" + "83666F6F" + "EF" + "FF" + "83666F6F");
		// Add object readers for two user-defined types
		r.addObjectReader(0, new ObjectReader<Foo>() {
			public Foo readObject(Reader r) throws IOException {
				r.readUserDefinedTag(0);
				return new Foo(r.readString());
			}
		});
		r.addObjectReader(255, new ObjectReader<Bar>() {
			public Bar readObject(Reader r) throws IOException {
				r.readUserDefinedTag(255);
				return new Bar(r.readString());
			}
		});
		// Test both tag formats, short and long
		assertTrue(r.hasUserDefined(0));
		assertEquals("foo", r.readUserDefined(0, Foo.class).s);
		assertTrue(r.hasUserDefined(255));
		assertEquals("foo", r.readUserDefined(255, Bar.class).s);
	}

	@Test
	public void testReadUserDefinedWithConsumer() throws Exception {
		setContents("C0" + "83666F6F" + "EF" + "FF" + "83666F6F");
		// Add object readers for two user-defined types
		r.addObjectReader(0, new ObjectReader<Foo>() {
			public Foo readObject(Reader r) throws IOException {
				r.readUserDefinedTag(0);
				return new Foo(r.readString());
			}
		});
		r.addObjectReader(255, new ObjectReader<Bar>() {
			public Bar readObject(Reader r) throws IOException {
				r.readUserDefinedTag(255);
				return new Bar(r.readString());
			}
		});
		// Add a consumer
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		r.addConsumer(new Consumer() {

			public void write(byte b) throws IOException {
				out.write(b);
			}

			public void write(byte[] b) throws IOException {
				out.write(b);
			}

			public void write(byte[] b, int off, int len) throws IOException {
				out.write(b, off, len);
			}
		});
		// Test both tag formats, short and long
		assertTrue(r.hasUserDefined(0));
		assertEquals("foo", r.readUserDefined(0, Foo.class).s);
		assertTrue(r.hasUserDefined(255));
		assertEquals("foo", r.readUserDefined(255, Bar.class).s);
		// Check that everything was passed to the consumer
		assertEquals("C0" + "83666F6F" + "EF" + "FF" + "83666F6F",
				StringUtils.toHexString(out.toByteArray()));
	}

	@Test
	public void testUnknownTagThrowsFormatException() throws Exception {
		setContents("C0" + "83666F6F");
		assertTrue(r.hasUserDefined(0));
		// No object reader has been added for tag 0
		try {
			r.readUserDefined(0, Foo.class);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testWrongClassThrowsFormatException() throws Exception {
		setContents("C0" + "83666F6F");
		// Add an object reader for tag 0, class Foo
		r.addObjectReader(0, new ObjectReader<Foo>() {
			public Foo readObject(Reader r) throws IOException {
				r.readUserDefinedTag(0);
				return new Foo(r.readString());
			}
		});
		assertTrue(r.hasUserDefined(0));
		// Trying to read the object as class Bar should throw a FormatException
		try {
			r.readUserDefined(0, Bar.class);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testReadListUsingObjectReader() throws Exception {
		setContents("A" + "1" + "C0" + "83666F6F");
		// Add an object reader for a user-defined type
		r.addObjectReader(0, new ObjectReader<Foo>() {
			public Foo readObject(Reader r) throws IOException {
				r.readUserDefinedTag(0);
				return new Foo(r.readString());
			}
		});
		// Check that the object reader is used for lists
		List<Foo> l = r.readList(Foo.class);
		assertEquals(1, l.size());
		assertEquals("foo", l.get(0).s);
	}

	@Test
	public void testReadMapUsingObjectReader() throws Exception {
		setContents("B" + "1" + "C0" + "83666F6F" + "C1" + "83626172");
		// Add object readers for two user-defined types
		r.addObjectReader(0, new ObjectReader<Foo>() {
			public Foo readObject(Reader r) throws IOException {
				r.readUserDefinedTag(0);
				return new Foo(r.readString());
			}
		});
		r.addObjectReader(1, new ObjectReader<Bar>() {
			public Bar readObject(Reader r) throws IOException {
				r.readUserDefinedTag(1);
				return new Bar(r.readString());
			}
		});
		// Check that the object readers are used for maps
		Map<Foo, Bar> m = r.readMap(Foo.class, Bar.class);
		assertEquals(1, m.size());
		Entry<Foo, Bar> e = m.entrySet().iterator().next();
		assertEquals("foo", e.getKey().s);
		assertEquals("bar", e.getValue().s);
	}

	@Test
	public void testMaxLengthAppliesInsideMap() throws Exception {
		setContents("B" + "1" + "83666F6F" + "93010203");
		r.setMaxStringLength(3);
		r.setMaxBytesLength(3);
		Map<String, Bytes> m = r.readMap(String.class, Bytes.class);
		String key = "foo";
		Bytes value = new Bytes(new byte[] {1, 2, 3});
		assertEquals(Collections.singletonMap(key, value), m);
		// The max string length should be applied inside the map
		setContents("B" + "1" + "83666F6F" + "93010203");
		r.setMaxStringLength(2);
		try {
			r.readMap(String.class, Bytes.class);
			fail();
		} catch(FormatException expected) {}
		// The max bytes length should be applied inside the map
		setContents("B" + "1" + "83666F6F" + "93010203");
		r.setMaxBytesLength(2);
		try {
			r.readMap(String.class, Bytes.class);
			fail();
		} catch(FormatException expected) {}
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

	private static class Foo {

		private final String s;

		private Foo(String s) {
			this.s = s;
		}
	}

	private static class Bar {

		private final String s;

		private Bar(String s) {
			this.s = s;
		}
	}
}
