package net.sf.briar.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import junit.framework.TestCase;
import net.sf.briar.util.ZipUtils.Callback;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ZipUtilsTest extends TestCase {

	private final File testDir = new File("test.tmp");

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

		Map<String, String> expected = new TreeMap<String, String>();
		expected.put("abc/def", "foo bar baz");
		checkZipEntries(dest, expected);
	}

	private void checkZipEntries(File f, Map<String, String> expected)
	throws IOException {
		Map<String, String> found = new TreeMap<String, String>();
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
		final File src1 = new File(testDir, "abc/def/1");
		final File src2 = new File(testDir, "abc/def/2");
		final File src3 = new File(testDir, "abc/3");
		Mockery context = new Mockery();
		final Callback callback = context.mock(Callback.class);
		context.checking(new Expectations() {{
			oneOf(callback).processingFile(src1);
			oneOf(callback).processingFile(src2);
			oneOf(callback).processingFile(src3);
		}});

		copyRecursively(callback);

		context.assertIsSatisfied();
	}

	@Test
	public void testCopyToZipRecursivelyNoCallback() throws IOException {
		copyRecursively(null);
	}

	private void copyRecursively(Callback callback) throws IOException {
		TestUtils.createFile(new File(testDir, "abc/def/1"), "one one one");
		TestUtils.createFile(new File(testDir, "abc/def/2"), "two two two");
		TestUtils.createFile(new File(testDir, "abc/3"), "three three three");

		File src = new File(testDir, "abc");
		File dest = new File(testDir, "dest");
		ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(dest));

		ZipUtils.copyToZipRecursively("ghi", src, zip, callback);
		zip.flush();
		zip.close();

		Map<String, String> expected = new TreeMap<String, String>();
		expected.put("ghi/def/1", "one one one");
		expected.put("ghi/def/2", "two two two");
		expected.put("ghi/3", "three three three");
		checkZipEntries(dest, expected);
	}

	@After
	public void tearDown() throws IOException {
		TestUtils.delete(testDir);
	}
}
