package org.briarproject.bramble.data;

import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfReader;
import org.briarproject.bramble.api.data.BdfWriter;
import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import static org.briarproject.bramble.api.data.BdfReader.DEFAULT_MAX_BUFFER_SIZE;
import static org.briarproject.bramble.api.data.BdfReader.DEFAULT_NESTED_LIMIT;
import static org.briarproject.bramble.util.StringUtils.fromHexString;
import static org.briarproject.bramble.util.StringUtils.toHexString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BdfReaderWriterIntegrationTest extends BrambleTestCase {

	@Test
	public void testConvertStringToCanonicalForm() throws Exception {
		// 'foo' as a STRING_16 (not canonical, should be a STRING_8)
		String hexIn = "42" + "0003" + "666F6F";
		InputStream in = new ByteArrayInputStream(fromHexString(hexIn));
		BdfReader r = new BdfReaderImpl(in, DEFAULT_NESTED_LIMIT,
				DEFAULT_MAX_BUFFER_SIZE, false); // Accept non-canonical
		String s = r.readString();
		assertEquals("foo", s);
		assertTrue(r.eof());
		// Convert the string back to BDF
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BdfWriter w = new BdfWriterImpl(out);
		w.writeString(s);
		w.flush();
		String hexOut = toHexString(out.toByteArray());
		// The BDF should now be in canonical form
		assertEquals("41" + "03" + "666F6F", hexOut);
	}

	@Test
	public void testConvertDictionaryToCanonicalForm() throws Exception {
		// A dictionary with keys in non-canonical order: 'foo' then 'bar'
		String hexIn = "70" + "41" + "03" + "666F6F" + "21" + "01"
				+ "41" + "03" + "626172" + "21" + "02" + "80";
		InputStream in = new ByteArrayInputStream(fromHexString(hexIn));
		BdfReader r = new BdfReaderImpl(in, DEFAULT_NESTED_LIMIT,
				DEFAULT_MAX_BUFFER_SIZE, false); // Accept non-canonical
		BdfDictionary d = r.readDictionary();
		assertEquals(2, d.size());
		assertTrue(r.eof());
		// The entries should be returned in canonical order
		Iterator<Entry<String, Object>> it = d.entrySet().iterator();
		Entry<String, Object> first = it.next();
		assertEquals("bar", first.getKey());
		assertEquals(2L, first.getValue());
		Entry<String, Object> second = it.next();
		assertEquals("foo", second.getKey());
		assertEquals(1L, second.getValue());

		// Convert a non-canonical map to BDF (use LinkedHashMap so we know
		// the entries will be iterated over in non-canonical order)
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("foo", 1);
		m.put("bar", 2);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BdfWriter w = new BdfWriterImpl(out);
		w.writeDictionary(m);
		w.flush();
		String hexOut = toHexString(out.toByteArray());
		// The entries should be in canonical order: 'bar' then 'foo'
		assertEquals("70" + "41" + "03" + "626172" + "21" + "02"
				+ "41" + "03" + "666F6F" + "21" + "01" + "80", hexOut);
	}
}
