package org.briarproject.serial;

import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;

import org.briarproject.BriarTestCase;
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
	public void testReadInteger() throws Exception {
		setContents("02" + "0000000000000000" + "02" + "FFFFFFFFFFFFFFFF"
				+ "02" + "7FFFFFFFFFFFFFFF" + "02" + "8000000000000000");
		assertEquals(0, r.readInteger());
		assertEquals(-1, r.readInteger());
		assertEquals(Long.MAX_VALUE, r.readInteger());
		assertEquals(Long.MIN_VALUE, r.readInteger());
		assertTrue(r.eof());
	}

	@Test
	public void testSkipInteger() throws Exception {
		setContents("02" + "0000000000000000");
		r.skipInteger();
		assertTrue(r.eof());
	}

	@Test
	public void testReadFloat() throws Exception {
		// http://babbage.cs.qc.edu/IEEE-754/Decimal.html
		// http://steve.hollasch.net/cgindex/coding/ieeefloat.html
		setContents("03" + "0000000000000000" + "03" + "3FF0000000000000"
				+ "03" + "4000000000000000" + "03" + "BFF0000000000000"
				+ "03" + "8000000000000000" + "03" + "FFF0000000000000"
				+ "03" + "7FF0000000000000" + "03" + "7FF8000000000000");
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
		setContents("03" + "0000000000000000");
		r.skipFloat();
		assertTrue(r.eof());
	}

	@Test
	public void testReadString() throws Exception {
		// "foo" and the empty string
		setContents("04" + "00000003" + "666F6F" + "04" + "00000000");
		assertEquals("foo", r.readString(Integer.MAX_VALUE));
		assertEquals("", r.readString(Integer.MAX_VALUE));
		assertTrue(r.eof());
	}

	@Test
	public void testReadStringMaxLength() throws Exception {
		// "foo" twice
		setContents("04" + "00000003" + "666F6F" +
				"04" + "00000003" + "666F6F");
		assertEquals("foo", r.readString(3));
		assertTrue(r.hasString());
		try {
			r.readString(2);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testSkipString() throws Exception {
		// "foo" and the empty string
		setContents("04" + "00000003" + "666F6F" + "04" + "00000000");
		r.skipString(Integer.MAX_VALUE);
		r.skipString(Integer.MAX_VALUE);
		assertTrue(r.eof());
	}

	@Test
	public void testSkipStringMaxLength() throws Exception {
		// "foo" twice
		setContents("04" + "00000003" + "666F6F" +
				"04" + "00000003" + "666F6F");
		r.skipString(3);
		assertTrue(r.hasString());
		try {
			r.skipString(2);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testReadBytes() throws Exception {
		// {1, 2, 3} and {}
		setContents("05" + "00000003" + "010203" + "05" + "00000000");
		assertArrayEquals(new byte[] {1, 2, 3}, r.readBytes(Integer.MAX_VALUE));
		assertArrayEquals(new byte[] {}, r.readBytes(Integer.MAX_VALUE));
		assertTrue(r.eof());
	}

	@Test
	public void testReadBytesMaxLength() throws Exception {
		// {1, 2, 3} twice
		setContents("05" + "00000003" + "010203" +
				"05" + "00000003" + "010203");
		assertArrayEquals(new byte[] {1, 2, 3}, r.readBytes(3));
		assertTrue(r.hasBytes());
		try {
			r.readBytes(2);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testSkipBytes() throws Exception {
		// {1, 2, 3} and {}
		setContents("05" + "00000003" + "010203" + "05" + "00000000");
		r.skipBytes(Integer.MAX_VALUE);
		r.skipBytes(Integer.MAX_VALUE);
		assertTrue(r.eof());
	}

	@Test
	public void testSkipBytesMaxLength() throws Exception {
		// {1, 2, 3} twice
		setContents("05" + "00000003" + "010203" +
				"05" + "00000003" + "010203");
		r.skipBytes(3);
		assertTrue(r.hasBytes());
		try {
			r.skipBytes(2);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testReadList() throws Exception {
		// A list containing 2, "foo", and 128
		setContents("06" + "02" + "0000000000000001" +
				"04" + "00000003" + "666F6F" +
				"02" + "0000000000000080" + "09");
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
		// A list containing 2, "foo", and 128
		setContents("06" + "02" + "0000000000000001" +
				"04" + "00000003" + "666F6F" +
				"02" + "0000000000000080" + "09");
		r.skipList();
		assertTrue(r.eof());
	}

	@Test
	public void testReadMap() throws Exception {
		// A map containing "foo" -> 123 and {} -> null
		setContents("07" + "04" + "00000003" + "666F6F" +
				"02" + "000000000000007B" + "05" + "00000000" + "0A" + "09");
		r.readMapStart();
		assertFalse(r.hasMapEnd());
		assertEquals("foo", r.readString(1000));
		assertFalse(r.hasMapEnd());
		assertEquals(123, r.readInteger());
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
		// A map containing "foo" -> 123 and {} -> null
		setContents("07" + "04" + "00000003" + "666F6F" +
				"02" + "000000000000007B" + "05" + "00000000" + "0A" + "09");
		r.skipMap();
		assertTrue(r.eof());
	}

	@Test
	public void testReadStruct() throws Exception {
		// Two empty structs with IDs 0 and 255
		setContents("0800" + "09" + "08FF" + "09");
		r.readStructStart(0);
		r.readStructEnd();
		r.readStructStart(255);
		r.readStructEnd();
		assertTrue(r.eof());
	}

	@Test
	public void testSkipStruct() throws Exception {
		// Two empty structs with IDs 0 and 255
		setContents("0800" + "09" + "08FF" + "09");
		r.skipStruct();
		r.skipStruct();
		assertTrue(r.eof());
	}

	@Test
	public void testSkipNestedStructMapAndList() throws Exception {
		// A struct containing a map containing two empty lists
		setContents("0800" + "07" + "06" + "09" + "06" + "09" + "09" + "09");
		r.skipStruct();
		assertTrue(r.eof());
	}

	@Test
	public void testReadNull() throws Exception {
		setContents("0A");
		r.readNull();
		assertTrue(r.eof());
	}

	@Test
	public void testSkipNull() throws Exception {
		setContents("0A");
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
