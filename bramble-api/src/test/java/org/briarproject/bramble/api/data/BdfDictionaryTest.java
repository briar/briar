package org.briarproject.bramble.api.data;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;

import static org.briarproject.bramble.api.data.BdfDictionary.NULL_VALUE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BdfDictionaryTest extends BrambleTestCase {

	@Test
	public void testConstructors() {
		assertEquals(Collections.<String, Object>emptyMap(),
				new BdfDictionary());
		assertEquals(Collections.singletonMap("foo", NULL_VALUE),
				new BdfDictionary(Collections.singletonMap("foo", NULL_VALUE)));
	}

	@Test
	public void testFactoryMethod() {
		assertEquals(Collections.<String, Object>emptyMap(),
				BdfDictionary.of());
		assertEquals(Collections.singletonMap("foo", NULL_VALUE),
				BdfDictionary.of(new BdfEntry("foo", NULL_VALUE)));
	}

	@Test
	public void testIntegerPromotion() throws Exception {
		BdfDictionary d = new BdfDictionary();
		d.put("foo", (byte) 1);
		d.put("bar", (short) 2);
		d.put("baz", 3);
		d.put("bam", 4L);
		assertEquals(Long.valueOf(1), d.getLong("foo"));
		assertEquals(Long.valueOf(2), d.getLong("bar"));
		assertEquals(Long.valueOf(3), d.getLong("baz"));
		assertEquals(Long.valueOf(4), d.getLong("bam"));
	}

	@Test
	public void testFloatPromotion() throws Exception {
		BdfDictionary d = new BdfDictionary();
		d.put("foo", 1F);
		d.put("bar", 2D);
		assertEquals(Double.valueOf(1), d.getDouble("foo"));
		assertEquals(Double.valueOf(2), d.getDouble("bar"));
	}

	@Test
	public void testByteArrayUnwrapping() throws Exception {
		BdfDictionary d = new BdfDictionary();
		d.put("foo", new byte[123]);
		d.put("bar", new Bytes(new byte[123]));
		byte[] foo = d.getRaw("foo");
		assertEquals(123, foo.length);
		assertArrayEquals(new byte[123], foo);
		byte[] bar = d.getRaw("bar");
		assertEquals(123, bar.length);
		assertArrayEquals(new byte[123], bar);
	}

	@Test
	public void testKeySetIteratorIsOrderedByKeys() throws Exception {
		BdfDictionary d = new BdfDictionary();
		d.put("a", 1);
		d.put("d", 4);
		d.put("b", 2);
		d.put("c", 3);
		// Keys should be returned in their natural order
		Iterator<String> it = d.keySet().iterator();
		assertTrue(it.hasNext());
		assertEquals("a", it.next());
		assertTrue(it.hasNext());
		assertEquals("b", it.next());
		assertTrue(it.hasNext());
		assertEquals("c", it.next());
		assertTrue(it.hasNext());
		assertEquals("d", it.next());
	}

	@Test
	public void testValuesIteratorIsOrderedByKeys() throws Exception {
		BdfDictionary d = new BdfDictionary();
		d.put("a", 1);
		d.put("d", 4);
		d.put("b", 2);
		d.put("c", 3);
		// Values should be returned in the natural order of their keys
		Iterator<Object> it = d.values().iterator();
		assertTrue(it.hasNext());
		assertEquals(1, it.next());
		assertTrue(it.hasNext());
		assertEquals(2, it.next());
		assertTrue(it.hasNext());
		assertEquals(3, it.next());
		assertTrue(it.hasNext());
		assertEquals(4, it.next());
	}

	@Test
	public void testEntrySetIteratorIsOrderedByKeys() throws Exception {
		BdfDictionary d = new BdfDictionary();
		d.put("a", 1);
		d.put("d", 4);
		d.put("b", 2);
		d.put("c", 3);
		// Entries should be returned in the natural order of their keys
		Iterator<Entry<String, Object>> it = d.entrySet().iterator();
		assertTrue(it.hasNext());
		Entry<String, Object> e = it.next();
		assertEquals("a", e.getKey());
		assertEquals(1, e.getValue());
		assertTrue(it.hasNext());
		e = it.next();
		assertEquals("b", e.getKey());
		assertEquals(2, e.getValue());
		assertTrue(it.hasNext());
		e = it.next();
		assertEquals("c", e.getKey());
		assertEquals(3, e.getValue());
		assertTrue(it.hasNext());
		e = it.next();
		assertEquals("d", e.getKey());
		assertEquals(4, e.getValue());
	}
}
