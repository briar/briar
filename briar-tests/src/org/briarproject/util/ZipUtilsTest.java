package org.briarproject.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.util.ZipUtils.Callback;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ZipUtilsTest extends BriarTestCase {

	private final File testDir = TestUtils.getTestDirectory();

	private final File f1 = new File(testDir, "abc/def/1");
	private final File f2 = new File(testDir, "abc/def/2");
	private final File f3 = new File(testDir, "abc/3");

	@Before
	public void setUp() {
		testDir.mkdirs();
	}

	@Test
	public void testCopyToZip() throws IOException {
		File src = new File(testDir, "src");
		File dest = new File(testDir, "dest");
		TestUtils.createFile(src, "foo bar baz");
		ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(dest));

		ZipUtils.copyToZip("abc/def", src, zip);
		zip.flush();
		zip.close();

		Map<String, String> expected = Collections.singletonMap("abc/def",
				"foo bar baz");
		checkZipEntries(dest, expected);
	}

	private void checkZipEntries(File f, Map<String, String> expected)
	throws IOException {
		Map<String, String> found = new HashMap<String, String>();
		assertTrue(f.exists());
		assertTrue(f.isFile());
		ZipInputStream unzip = new ZipInputStream(new FileInputStream(f));
		ZipEntry entry;
		while((entry = unzip.getNextEntry()) != null) {
			String name = entry.getName();
			Scanner s = new Scanner(unzip);
			assertTrue(s.hasNextLine());
			String contents = s.nextLine();
			assertFalse(s.hasNextLine());
			unzip.closeEntry();
			found.put(name, contents);
		}
		unzip.close();
		assertEquals(expected.size(), found.size());
		for(String name : expected.keySet()) {
			String contents = found.get(name);
			assertNotNull(contents);
			assertEquals(expected.get(name), contents);
		}
	}

	@Test
	public void testCopyToZipRecursively() throws IOException {
		Mockery context = new Mockery();
		final Callback callback = context.mock(Callback.class);
		context.checking(new Expectations() {{
			oneOf(callback).processingFile(f1);
			oneOf(callback).processingFile(f2);
			oneOf(callback).processingFile(f3);
		}});

		copyRecursively(callback);

		context.assertIsSatisfied();
	}

	@Test
	public void testCopyToZipRecursivelyNoCallback() throws IOException {
		copyRecursively(null);
	}

	private void copyRecursively(Callback callback) throws IOException {
		TestUtils.createFile(f1, "one one one");
		TestUtils.createFile(f2, "two two two");
		TestUtils.createFile(f3, "three three three");
		File src = new File(testDir, "abc");
		File dest = new File(testDir, "dest");
		ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(dest));

		ZipUtils.copyToZipRecursively("ghi", src, zip, callback);
		zip.flush();
		zip.close();

		Map<String, String> expected = new HashMap<String, String>();
		expected.put("ghi/def/1", "one one one");
		expected.put("ghi/def/2", "two two two");
		expected.put("ghi/3", "three three three");
		checkZipEntries(dest, expected);
	}

	@Test
	public void testUnzipStream() throws IOException {
		Mockery context = new Mockery();
		final Callback callback = context.mock(Callback.class);
		context.checking(new Expectations() {{
			oneOf(callback).processingFile(f1);
			oneOf(callback).processingFile(f2);
			oneOf(callback).processingFile(f3);
		}});

		unzipStream(null, callback);

		context.assertIsSatisfied();

		assertTrue(f1.exists());
		assertTrue(f1.isFile());
		assertEquals("one one one".length(), f1.length());
		assertTrue(f2.exists());
		assertTrue(f2.isFile());
		assertEquals("two two two".length(), f2.length());
		assertTrue(f3.exists());
		assertTrue(f3.isFile());
		assertEquals("three three three".length(), f3.length());
	}

	@Test
	public void testUnzipStreamWithRegex() throws IOException {
		Mockery context = new Mockery();
		final Callback callback = context.mock(Callback.class);
		context.checking(new Expectations() {{
			oneOf(callback).processingFile(f1);
			oneOf(callback).processingFile(f2);
		}});

		unzipStream("^abc/def/.*", callback);

		context.assertIsSatisfied();

		assertTrue(f1.exists());
		assertTrue(f1.isFile());
		assertEquals("one one one".length(), f1.length());
		assertTrue(f2.exists());
		assertTrue(f2.isFile());
		assertEquals("two two two".length(), f2.length());
		assertFalse(f3.exists());
	}

	@Test
	public void testUnzipStreamNoCallback() throws IOException {
		unzipStream(null, null);

		assertTrue(f1.exists());
		assertTrue(f1.isFile());
		assertEquals("one one one".length(), f1.length());
		assertTrue(f2.exists());
		assertTrue(f2.isFile());
		assertEquals("two two two".length(), f2.length());
		assertTrue(f3.exists());
		assertTrue(f3.isFile());
		assertEquals("three three three".length(), f3.length());
	}

	private void unzipStream(String regex, Callback callback)
	throws IOException {
		TestUtils.createFile(f1, "one one one");
		TestUtils.createFile(f2, "two two two");
		TestUtils.createFile(f3, "three three three");
		File src = new File(testDir, "abc");
		File dest = new File(testDir, "dest");
		ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(dest));
		ZipUtils.copyToZipRecursively(src.getName(), src, zip, null);
		zip.flush();
		zip.close();
		TestUtils.delete(src);

		InputStream in = new FileInputStream(dest);
		ZipUtils.unzipStream(in, testDir, regex, callback);
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}
}
