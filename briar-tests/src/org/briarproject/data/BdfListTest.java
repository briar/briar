package org.briarproject.data;

import org.briarproject.BriarTestCase;
import org.briarproject.api.Bytes;
import org.briarproject.api.data.BdfList;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.briarproject.api.data.BdfDictionary.NULL_VALUE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class BdfListTest extends BriarTestCase {

	@Test
	public void testConstructors() {
		assertEquals(Collections.emptyList(), new BdfList());
		assertEquals(Arrays.asList(1, 2, NULL_VALUE),
				new BdfList(Arrays.asList(1, 2, NULL_VALUE)));
	}

	@Test
	public void testFactoryMethod() {
		assertEquals(Collections.emptyList(), BdfList.of());
		assertEquals(Arrays.asList(1, 2, NULL_VALUE),
				BdfList.of(1, 2, NULL_VALUE));
	}

	@Test
	public void testIntegerPromotion() throws Exception {
		BdfList list = new BdfList();
		list.add((byte) 1);
		list.add((short) 2);
		list.add(3);
		list.add(4L);
		assertEquals(Long.valueOf(1), list.getLong(0));
		assertEquals(Long.valueOf(2), list.getLong(1));
		assertEquals(Long.valueOf(3), list.getLong(2));
		assertEquals(Long.valueOf(4), list.getLong(3));
	}

	@Test
	public void testFloatPromotion() throws Exception {
		BdfList list = new BdfList();
		list.add(1F);
		list.add(2D);
		assertEquals(Double.valueOf(1), list.getDouble(0));
		assertEquals(Double.valueOf(2), list.getDouble(1));
	}

	@Test
	public void testByteArrayUnwrapping() throws Exception {
		BdfList list = new BdfList();
		list.add(new byte[123]);
		list.add(new Bytes(new byte[123]));
		byte[] first = list.getRaw(0);
		assertEquals(123, first.length);
		assertArrayEquals(new byte[123], first);
		byte[] second = list.getRaw(1);
		assertEquals(123, second.length);
		assertArrayEquals(new byte[123], second);
	}
}
