package net.sf.briar.util;

import junit.framework.TestCase;

import org.junit.Test;

public class StringUtilsTest extends TestCase {

	@Test
	public void testHead() {
		String head = StringUtils.head("123456789", 5);
		assertEquals("12345...", head);
	}

	@Test
	public void testTail() {
		String tail = StringUtils.tail("987654321", 5);
		assertEquals("...54321", tail);
	}
}
