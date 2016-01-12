package org.briarproject.data;

import org.briarproject.BriarTestCase;
import org.briarproject.api.FormatException;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.db.Metadata;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class MetadataEncoderParserImplTest extends BriarTestCase {

	MetadataEncoderImpl e = new MetadataEncoderImpl();
	MetadataParserImpl p = new MetadataParserImpl();
	BdfDictionary d = new BdfDictionary();

	@Test
	public void testBoolean() throws FormatException {
		d.put("test", true);
		Metadata metadata = e.encode(d);

		assertEquals(p.parse(metadata).getBoolean("test", false), true);
	}

	@Test
	public void testInteger() throws FormatException {
		d.put("test", 1337);
		Metadata metadata = e.encode(d);

		assertEquals((long) p.parse(metadata).getInteger("test", 0L), 1337L);
	}

	@Test
	public void testLong() throws FormatException {
		d.put("test", Long.MAX_VALUE);
		Metadata metadata = e.encode(d);

		assertEquals((long) p.parse(metadata).getInteger("test", 0L),
				Long.MAX_VALUE);
	}

	@Test
	public void testDouble() throws FormatException {
		d.put("test", Double.MAX_VALUE);
		Metadata metadata = e.encode(d);

		assertEquals(p.parse(metadata).getFloat("test", 0.0),
				Double.MAX_VALUE, 0);
	}

	@Test
	public void testFloat() throws FormatException {
		d.put("test", Float.MIN_NORMAL);
		Metadata metadata = e.encode(d);

		assertEquals(p.parse(metadata).getFloat("test", 0.0),
				Float.MIN_NORMAL, 0);
	}

	@Test
	public void testString() throws FormatException {
		d.put("test", "abc");
		Metadata metadata = e.encode(d);

		assertEquals(p.parse(metadata).getString("test", null), "abc");
	}

	@Test
	public void testUtf8String() throws FormatException {
		d.put("test", "abcdefghilkmnopqrst ������ \uFDD0\uFDD1\uFDD2\uFDD3");
		Metadata metadata = e.encode(d);

		assertEquals(p.parse(metadata).getString("test", null),
				"abcdefghilkmnopqrst ������ \uFDD0\uFDD1\uFDD2\uFDD3");
	}

	@Test
	public void testRaw() throws FormatException {
		byte[] b = "\uFDD0\uFDD1\uFDD2\uFDD3".getBytes();
		d.put("test", b);
		Metadata metadata = e.encode(d);

		assertEquals(p.parse(metadata).getRaw("test", null), b);
	}

	@Test
	public void testList() throws FormatException {
		List<Long> l = new ArrayList<Long>(4);
		l.add(42L);
		l.add(1337L);
		l.add(Long.MIN_VALUE);
		l.add(Long.MAX_VALUE);

		d.put("test", l);
		Metadata metadata = e.encode(d);

		assertArrayEquals(p.parse(metadata).getList("test", null).toArray(),
				l.toArray());
	}

	@Test
	public void testDictionary() throws FormatException {
		Map<String, Boolean> m = new HashMap<String, Boolean>();
		m.put("1", true);
		m.put("2", false);

		d.put("test", m);
		Metadata metadata = e.encode(d);

		assertEquals(p.parse(metadata).getDictionary("test", null)
				.getBoolean("1", false), true);

		assertEquals(p.parse(metadata).getDictionary("test", null)
				.getBoolean("2", true), false);
	}

	@Test
	public void testComplexDictionary() throws FormatException {
		Map<String, List> m = new HashMap<String, List>();
		List<String> one = new ArrayList<String>(3);
		one.add("����");
		one.add("������");
		one.add("����");
		m.put("One", one);
		List<String> two = new ArrayList<String>(2);
		two.add("\u0080");
		two.add("\uD800\uDC00");
		m.put("Two", two);
		d.put("test", m);

		Map<String, Boolean> m2 = new HashMap<String, Boolean>();
		m2.put("should be true", true);
		d.put("another test", m2);

		Metadata metadata = e.encode(d);

		assertEquals(p.parse(metadata).getDictionary("test", null)
				.getList("One", null).get(0), "����");
		assertEquals(p.parse(metadata).getDictionary("test", null)
				.getList("One", null).get(1), "������");
		assertEquals(p.parse(metadata).getDictionary("test", null)
				.getList("One", null).get(2), "����");
		assertEquals(p.parse(metadata).getDictionary("test", null)
				.getList("Two", null).get(0), "\u0080");
		assertEquals(p.parse(metadata).getDictionary("test", null)
				.getList("Two", null).get(0), "\uD800\uDC00");

		assertEquals(p.parse(metadata).getDictionary("another test", null)
				.getBoolean("should be true", false), true);
	}

}
