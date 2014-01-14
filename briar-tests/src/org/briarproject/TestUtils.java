package org.briarproject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.briarproject.api.UniqueId;

public class TestUtils {

	private static final AtomicInteger nextTestDir =
		new AtomicInteger((int) (Math.random() * 1000 * 1000));
	private static final Random random = new Random();

	public static void delete(File f) {
		if(f.isDirectory()) for(File child : f.listFiles()) delete(child);
		f.delete();
	}

	public static void createFile(File f, String s) throws IOException {
		f.getParentFile().mkdirs();
		PrintStream out = new PrintStream(new FileOutputStream(f));
		out.print(s);
		out.flush();
		out.close();
	}

	public static File getTestDirectory() {
		int name = nextTestDir.getAndIncrement();
		File testDir = new File("test.tmp/" + name);
		return testDir;
	}

	public static void deleteTestDirectory(File testDir) {
		delete(testDir);
		testDir.getParentFile().delete(); // Delete if empty
	}

	public static byte[] getRandomId() {
		byte[] b = new byte[UniqueId.LENGTH];
		random.nextBytes(b);
		return b;
	}

	public static String createRandomString(int length) throws Exception {
		StringBuilder s = new StringBuilder(length);
		for(int i = 0; i < length; i++)
			s.append((char) ('a' + random.nextInt(26)));
		return s.toString();
	}
}
