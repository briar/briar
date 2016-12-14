package org.briarproject.bramble.data;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class MetadataEncoderParserIntegrationTest extends BrambleTestCase {

	private MetadataEncoderImpl e;
	private MetadataParserImpl p;
	private BdfDictionary d;

	@Before
	public void before() {
		e = new MetadataEncoderImpl(new BdfWriterFactoryImpl());
		p = new MetadataParserImpl(new BdfReaderFactoryImpl());
		d = new BdfDictionary();
	}

	@Test
	public void testBoolean() throws FormatException {
		d.put("test", true);
		Metadata metadata = e.encode(d);

		assertEquals(true, p.parse(metadata).getBoolean("test", false));
	}

	@Test
	public void testInteger() throws FormatException {
		d.put("test", 1337);
		Metadata metadata = e.encode(d);

		assertEquals(1337L, (long) p.parse(metadata).getLong("test", 0L));
	}

	@Test
	public void testLong() throws FormatException {
		d.put("test", Long.MAX_VALUE);
		Metadata metadata = e.encode(d);

		assertEquals(Long.MAX_VALUE,
				(long) p.parse(metadata).getLong("test", 0L));
	}

	@Test
	public void testDouble() throws FormatException {
		d.put("test", Double.MAX_VALUE);
		Metadata metadata = e.encode(d);

		assertEquals(Double.MAX_VALUE,
				p.parse(metadata).getDouble("test", 0.0), 0);
	}

	@Test
	public void testFloat() throws FormatException {
		d.put("test", Float.MIN_NORMAL);
		Metadata metadata = e.encode(d);

		assertEquals(Float.MIN_NORMAL,
				p.parse(metadata).getDouble("test", 0.0), 0);
	}

	@Test
	public void testString() throws FormatException {
		d.put("test", "abc");
		Metadata metadata = e.encode(d);

		assertEquals("abc", p.parse(metadata).getString("test", null));
	}

	@Test
	public void testUtf8String() throws FormatException {
		d.put("test", "abcdefghilkmnopqrst \uFDD0\uFDD1\uFDD2\uFDD3");
		Metadata metadata = e.encode(d);

		assertEquals("abcdefghilkmnopqrst \uFDD0\uFDD1\uFDD2\uFDD3",
				p.parse(metadata).getString("test", null));
	}

	@Test
	public void testRaw() throws FormatException {
		byte[] b = "\uFDD0\uFDD1\uFDD2\uFDD3".getBytes();
		d.put("test", b);
		Metadata metadata = e.encode(d);

		assertArrayEquals(b, p.parse(metadata).getRaw("test", null));
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

		assertArrayEquals(l.toArray(),
				p.parse(metadata).getList("test", null).toArray());
	}

	@Test
	public void testDictionary() throws FormatException {
		Map<String, Boolean> m = new HashMap<String, Boolean>();
		m.put("1", true);
		m.put("2", false);

		d.put("test", m);
		Metadata metadata = e.encode(d);

		assertEquals(true, p.parse(metadata).getDictionary("test", null)
				.getBoolean("1", false));

		assertEquals(false, p.parse(metadata).getDictionary("test", null)
				.getBoolean("2", true));
	}

	@Test
	public void testComplexDictionary() throws FormatException {
		Map<String, List> m = new HashMap<String, List>();
		List<String> one = new ArrayList<String>(3);
		one.add("\uFDD0");
		one.add("\uFDD1");
		one.add("\uFDD2");
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

		assertEquals("\uFDD0", p.parse(metadata).getDictionary("test", null)
				.getList("One", null).get(0));
		assertEquals("\uFDD1", p.parse(metadata).getDictionary("test", null)
				.getList("One", null).get(1));
		assertEquals("\uFDD2", p.parse(metadata).getDictionary("test", null)
				.getList("One", null).get(2));
		assertEquals("\u0080", p.parse(metadata).getDictionary("test", null)
				.getList("Two", null).get(0));
		assertEquals("\uD800\uDC00",
				p.parse(metadata).getDictionary("test", null)
						.getList("Two", null).get(1));

		assertEquals(true, p.parse(metadata).getDictionary("another test", null)
				.getBoolean("should be true", false));
	}
}
