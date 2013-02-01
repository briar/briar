package net.sf.briar.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import net.sf.briar.BriarTestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.util.FileUtils.Callback;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FileUtilsTest extends BriarTestCase {

	private final File testDir = TestUtils.getTestDirectory();

	@Before
	public void setUp() {
		testDir.mkdirs();
	}

	@Test
	public void testCreateTempFile() throws IOException {
		File temp = FileUtils.createTempFile();
		assertTrue(temp.exists());
		assertTrue(temp.isFile());
		assertEquals(0, temp.length());
		temp.delete();
	}

	@Test
	public void testCopy() throws IOException {
		File src = new File(testDir, "src");
		File dest = new File(testDir, "dest");
		TestUtils.createFile(src, "Foo bar\r\nBar foo\r\n");
		long length = src.length();

		FileUtils.copy(src, dest);

		assertEquals(length, dest.length());
		Scanner in = new Scanner(dest);
		assertTrue(in.hasNextLine());
		assertEquals("Foo bar", in.nextLine());
		assertTrue(in.hasNextLine());
		assertEquals("Bar foo", in.nextLine());
		assertFalse(in.hasNext());
		in.close();
	}

	@Test
	public void testCopyFromStream() throws IOException {
		File src = new File(testDir, "src");
		File dest = new File(testDir, "dest");
		TestUtils.createFile(src, "Foo bar\r\nBar foo\r\n");
		long length = src.length();
		InputStream is = new FileInputStream(src);
		is.skip(4);

		FileUtils.copy(is, dest);

		assertEquals(length - 4, dest.length());
		Scanner in = new Scanner(dest);
		assertTrue(in.hasNextLine());
		assertEquals("bar", in.nextLine());
		assertTrue(in.hasNextLine());
		assertEquals("Bar foo", in.nextLine());
		assertFalse(in.hasNext());
		in.close();
	}

	@Test
	public void testCopyRecursively() throws IOException {
		final File dest1 = new File(testDir, "dest/abc/def/1");
		final File dest2 = new File(testDir, "dest/abc/def/2");
		final File dest3 = new File(testDir, "dest/abc/3");
		Mockery context = new Mockery();
		final Callback callback = context.mock(Callback.class);
		context.checking(new Expectations() {{
			oneOf(callback).processingFile(dest1);
			oneOf(callback).processingFile(dest2);
			oneOf(callback).processingFile(dest3);
		}});

		copyRecursively(callback);

		context.assertIsSatisfied();
	}

	@Test
	public void testCopyRecursivelyNoCallback() throws IOException {
		copyRecursively(null);
	}

	private void copyRecursively(Callback callback) throws IOException {
		TestUtils.createFile(new File(testDir, "abc/def/1"), "one one one");
		TestUtils.createFile(new File(testDir, "abc/def/2"), "two two two");
		TestUtils.createFile(new File(testDir, "abc/3"), "three three three");

		File dest = new File(testDir, "dest");
		dest.mkdir();

		FileUtils.copyRecursively(new File(testDir, "abc"), dest, callback);

		File dest1 = new File(testDir, "dest/abc/def/1");
		assertTrue(dest1.exists());
		assertTrue(dest1.isFile());
		assertEquals("one one one".length(), dest1.length());
		File dest2 = new File(testDir, "dest/abc/def/2");
		assertTrue(dest2.exists());
		assertTrue(dest2.isFile());
		assertEquals("two two two".length(), dest2.length());
		File dest3 = new File(testDir, "dest/abc/3");
		assertTrue(dest3.exists());
		assertTrue(dest3.isFile());
		assertEquals("three three three".length(), dest3.length());
	}

	@Test
	public void testDeleteFile() throws IOException {
		File foo = new File(testDir, "foo");
		foo.createNewFile();
		assertTrue(foo.exists());

		FileUtils.delete(foo);

		assertFalse(foo.exists());
	}

	@Test
	public void testDeleteDirectory() throws IOException {
		File f1 = new File(testDir, "abc/def/1");
		File f2 = new File(testDir, "abc/def/2");
		File f3 = new File(testDir, "abc/3");
		File abc = new File(testDir, "abc");
		File def = new File(testDir, "abc/def");
		TestUtils.createFile(f1, "one one one");
		TestUtils.createFile(f2, "two two two");
		TestUtils.createFile(f3, "three three three");

		assertTrue(f1.exists());
		assertTrue(f2.exists());
		assertTrue(f3.exists());
		assertTrue(abc.exists());
		assertTrue(def.exists());

		FileUtils.delete(def);

		assertFalse(f1.exists());
		assertFalse(f2.exists());
		assertTrue(f3.exists());
		assertTrue(abc.exists());
		assertFalse(def.exists());
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}
}
