package org.briarproject.data;

import org.briarproject.BriarTestCase;
import org.briarproject.api.Bytes;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.junit.Test;

import java.util.Collections;

import static org.briarproject.api.data.BdfDictionary.NULL_VALUE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class BdfDictionaryTest extends BriarTestCase {

	@Test
	public void testConstructors() {
		assertEquals(Collections.emptyMap(), new BdfDictionary());
		assertEquals(Collections.singletonMap("foo", NULL_VALUE),
				new BdfDictionary(Collections.singletonMap("foo", NULL_VALUE)));
	}

	@Test
	public void testFactoryMethod() {
		assertEquals(Collections.emptyMap(), BdfDictionary.of());
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
		assertEquals(Long.valueOf(1), d.getInteger("foo"));
		assertEquals(Long.valueOf(2), d.getInteger("bar"));
		assertEquals(Long.valueOf(3), d.getInteger("baz"));
		assertEquals(Long.valueOf(4), d.getInteger("bam"));
	}

	@Test
	public void testFloatPromotion() throws Exception {
		BdfDictionary d = new BdfDictionary();
		d.put("foo", 1F);
		d.put("bar", 2D);
		assertEquals(Double.valueOf(1), d.getFloat("foo"));
		assertEquals(Double.valueOf(2), d.getFloat("bar"));
	}

	@Test
	public void testByteArrayUnwrapping() throws Exception {
		BdfDictionary d = new BdfDictionary();
		d.put("foo", new byte[123]);
		d.put("bar", new Bytes(new byte[123]));
		assertArrayEquals(new byte[123], d.getRaw("foo"));
		assertArrayEquals(new byte[123], d.getRaw("bar"));
	}
}
