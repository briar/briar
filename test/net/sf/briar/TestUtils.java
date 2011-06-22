package net.sf.briar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

public class TestUtils {

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
}
