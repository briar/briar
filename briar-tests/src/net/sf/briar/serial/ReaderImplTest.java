package net.sf.briar.serial;

import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.FormatException;
import net.sf.briar.util.StringUtils;

import org.junit.Test;

public class ReaderImplTest extends BriarTestCase {

	private ByteArrayInputStream in = null;
	private ReaderImpl r = null;

	@Test
	public void testReadBoolean() throws Exception {
		setContents("FF" + "FE");
		assertFalse(r.readBoolean());
		assertTrue(r.readBoolean());
		assertTrue(r.eof());
	}

	@Test
	public void testSkipBoolean() throws Exception {
		setContents("FF" + "FE");
		r.skipBoolean();
		r.skipBoolean();
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
	public void testSkipInt8() throws Exception {
		setContents("FD00");
		r.skipInt8();
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
	public void testSkipInt16() throws Exception {
		setContents("FC0000");
		r.skipInt16();
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
	public void testSkipInt32() throws Exception {
		setContents("FB00000000");
		r.skipInt32();
		assertTrue(r.eof());
	}

	@Test
	public void testReadInt64() throws Exception {
		setContents("FA0000000000000000" + "FAFFFFFFFFFFFFFFFF"
				+ "FA7FFFFFFFFFFFFFFF" + "FA8000000000000000");
		assertEquals(0, r.readInt64());
		assertEquals(-1, r.readInt64());
		assertEquals(Long.MAX_VALUE, r.readInt64());
		assertEquals(Long.MIN_VALUE, r.readInt64());
		assertTrue(r.eof());
	}

	@Test
	public void testSkipInt64() throws Exception {
		setContents("FA0000000000000000");
		r.skipInt64();
		assertTrue(r.eof());
	}

	@Test
	public void testReadIntAny() throws Exception {
		setContents("00" + "7F" + "FD80" + "FDFF" + "FC0080" + "FC7FFF"
				+ "FB00008000" + "FB7FFFFFFF" + "FA0000000080000000");
		assertEquals(0, r.readIntAny());
		assertEquals(127, r.readIntAny());
		assertEquals(-128, r.readIntAny());
		assertEquals(-1, r.readIntAny());
		assertEquals(128, r.readIntAny());
		assertEquals(32767, r.readIntAny());
		assertEquals(32768, r.readIntAny());
		assertEquals(2147483647, r.readIntAny());
		assertEquals(2147483648L, r.readIntAny());
		assertTrue(r.eof());
	}

	@Test
	public void testSkipIntAny() throws Exception {
		setContents("00" + "FD00" + "FC0000" + "FB00000000"
				+ "FA0000000000000000");
		r.skipIntAny();
		r.skipIntAny();
		r.skipIntAny();
		r.skipIntAny();
		r.skipIntAny();
		assertTrue(r.eof());
	}

	@Test
	public void testReadFloat32() throws Exception {
		// http://babbage.cs.qc.edu/IEEE-754/Decimal.html
		// http://steve.hollasch.net/cgindex/coding/ieeefloat.html
		setContents("F900000000" + "F93F800000" + "F940000000" + "F9BF800000"
				+ "F980000000" + "F9FF800000" + "F97F800000" + "F97FC00000");
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
	public void testSkipFloat32() throws Exception {
		setContents("F900000000");
		r.skipFloat32();
		assertTrue(r.eof());
	}

	@Test
	public void testReadFloat64() throws Exception {
		setContents("F80000000000000000" + "F83FF0000000000000"
				+ "F84000000000000000" + "F8BFF0000000000000"
				+ "F88000000000000000" + "F8FFF0000000000000"
				+ "F87FF0000000000000" + "F87FF8000000000000");
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
	public void testSkipFloat64() throws Exception {
		setContents("F80000000000000000");
		r.skipFloat64();
		assertTrue(r.eof());
	}

	@Test
	public void testReadString() throws Exception {
		setContents("F703666F6F" + "F700");
		assertEquals("foo", r.readString(Integer.MAX_VALUE));
		assertEquals("", r.readString(Integer.MAX_VALUE));
		assertTrue(r.eof());
	}

	@Test
	public void testReadStringMaxLength() throws Exception {
		setContents("F703666F6F" + "F703666F6F");
		assertEquals("foo", r.readString(3));
		try {
			r.readString(2);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testSkipString() throws Exception {
		setContents("F703666F6F" + "F700");
		r.skipString(Integer.MAX_VALUE);
		r.skipString(Integer.MAX_VALUE);
		assertTrue(r.eof());
	}

	@Test
	public void testSkipStringMaxLength() throws Exception {
		setContents("F703666F6F" + "F703666F6F");
		r.skipString(3);
		try {
			r.skipString(2);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testReadBytes() throws Exception {
		setContents("F603010203" + "F600");
		assertArrayEquals(new byte[] {1, 2, 3}, r.readBytes(Integer.MAX_VALUE));
		assertArrayEquals(new byte[] {}, r.readBytes(Integer.MAX_VALUE));
		assertTrue(r.eof());
	}

	@Test
	public void testReadBytesMaxLength() throws Exception {
		setContents("F603010203" + "F603010203");
		assertArrayEquals(new byte[] {1, 2, 3}, r.readBytes(3));
		try {
			r.readBytes(2);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testSkipBytes() throws Exception {
		setContents("F603010203" + "F600");
		r.skipBytes(Integer.MAX_VALUE);
		r.skipBytes(Integer.MAX_VALUE);
		assertTrue(r.eof());
	}

	@Test
	public void testSkipBytesMaxLength() throws Exception {
		setContents("F603010203" + "F603010203");
		r.skipBytes(3);
		try {
			r.skipBytes(2);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testReadList() throws Exception {
		setContents("F5" + "01" + "F703666F6F" + "FC0080" + "F2");
		r.readListStart();
		assertFalse(r.hasListEnd());
		assertEquals((byte) 1, r.readIntAny());
		assertFalse(r.hasListEnd());
		assertEquals("foo", r.readString(1000));
		assertFalse(r.hasListEnd());
		assertEquals((short) 128, r.readIntAny());
		assertTrue(r.hasListEnd());
		r.readListEnd();
		assertTrue(r.eof());
	}

	@Test
	public void testSkipList() throws Exception {
		setContents("F5" + "01" + "F703666F6F" + "FC0080" + "F2");
		r.skipList();
		assertTrue(r.eof());
	}

	@Test
	public void testReadMap() throws Exception {
		setContents("F4" + "F703666F6F" + "7B" + "F600" + "F1" + "F2");
		r.readMapStart();
		assertFalse(r.hasMapEnd());
		assertEquals("foo", r.readString(1000));
		assertFalse(r.hasMapEnd());
		assertEquals((byte) 123, r.readIntAny());
		assertFalse(r.hasMapEnd());
		assertArrayEquals(new byte[] {}, r.readBytes(1000));
		assertFalse(r.hasMapEnd());
		assertTrue(r.hasNull());
		r.readNull();
		assertTrue(r.hasMapEnd());
		r.readMapEnd();
		assertTrue(r.eof());
	}

	@Test
	public void testSkipMap() throws Exception {
		setContents("F4" + "F703666F6F" + "7B" + "F600" + "F1" + "F2");
		r.skipMap();
		assertTrue(r.eof());
	}

	@Test
	public void testReadStruct() throws Exception {
		// Two empty structs with IDs 0 and 255
		setContents("F300" + "F2" + "F3FF" + "F2");
		r.readStructStart(0);
		r.readStructEnd();
		r.readStructStart(255);
		r.readStructEnd();
		assertTrue(r.eof());
	}

	@Test
	public void testSkipStruct() throws Exception {
		// Two empty structs with IDs 0 and 255
		setContents("F300" + "F2" + "F3FF" + "F2");
		r.skipStruct();
		r.skipStruct();
		assertTrue(r.eof());
	}

	@Test
	public void testSkipNestedStructMapAndList() throws Exception {
		// A struct containing a map containing two empty lists
		setContents("F300" + "F4" + "F5" + "F2" + "F5" + "F2" + "F2" + "F2");
		r.skipStruct();
		assertTrue(r.eof());
	}

	@Test
	public void testReadNull() throws Exception {
		setContents("F1");
		r.readNull();
		assertTrue(r.eof());
	}

	@Test
	public void testSkipNull() throws Exception {
		setContents("F1");
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
