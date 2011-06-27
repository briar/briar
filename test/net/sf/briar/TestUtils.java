package net.sf.briar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;

public class TestUtils {

	private static final AtomicInteger nextTestDir =
		new AtomicInteger((int) (Math.random() * 1000 * 1000));

	public static void delete(File f) throws IOException {
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
}
