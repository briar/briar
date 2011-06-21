package net.sf.briar.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Scanner;

import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FileUtilsTest extends TestCase {

	private final File testDir = new File("test.tmp");

	@Before
	public void setUp() {
		testDir.mkdirs();
	}

	@Test
	public void testCopy() throws IOException {
		File src = new File(testDir, "src");
		File dest = new File(testDir, "dest");

		PrintStream out = new PrintStream(new FileOutputStream(src));
		out.print("Foo bar\r\nBar foo\r\n");
		out.flush();
		out.close();
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

		src.delete();
		dest.delete();
	}

	@After
	public void tearDown() throws IOException {
		delete(testDir);
	}

	private static void delete(File f) throws IOException {
		if(f.isDirectory()) for(File child : f.listFiles()) delete(child);
		f.delete();
	}
}
