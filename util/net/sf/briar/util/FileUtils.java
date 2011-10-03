package net.sf.briar.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.CodeSource;

public class FileUtils {

	/**
	 * Returns the directory where Briar is installed.
	 */
	public static File getBriarDirectory() {
		CodeSource c = FileUtils.class.getProtectionDomain().getCodeSource();
		File f = new File(c.getLocation().getPath());
		assert f.exists();
		if(f.isFile()) {
			// Running from a jar - return the jar's grandparent
			f = f.getParentFile().getParentFile();
		} else {
			// Running from Eclipse
			f = new File(f.getParentFile(), "Briar");
		}
		assert f.exists();
		assert f.isDirectory();
		return f;
	}

	/**
	 * Creates and returns a temporary file.
	 */
	public static File createTempFile() throws IOException {
		String rand = String.valueOf(1000 + (int) (Math.random() * 9000));
		return File.createTempFile(rand, null);
	}

	/**
	 * Copies the contents of the source file to the destination file.
	 */
	public static void copy(File src, File dest) throws IOException {
		FileInputStream in = new FileInputStream(src);
		copy(in, dest);
	}

	/**
	 * Copies the contents of the input stream to the destination file.
	 */
	public static void copy(InputStream in, File dest) throws IOException {
		FileOutputStream out = new FileOutputStream(dest);
		byte[] buf = new byte[1024];
		int i;
		while((i = in.read(buf, 0, buf.length)) != -1) out.write(buf, 0, i);
		in.close();
		out.flush();
		out.close();
	}

	/**
	 * Copies the source file or directory to the destination directory. If the
	 * callback is not null it's called once for each file created.
	 */
	public static void copyRecursively(File src, File dest, Callback callback)
	throws IOException {
		assert dest.exists();
		assert dest.isDirectory();
		dest = new File(dest, src.getName());
		if(src.isDirectory()) {
			dest.mkdir();
			for(File f : src.listFiles()) copyRecursively(f, dest, callback);
		} else {
			if(callback != null) callback.processingFile(dest);
			copy(src, dest);
		}
	}

	public static void delete(File f) throws IOException {
		if(f.isDirectory() && !isSymlink(f)) {
			for(File child : f.listFiles()) delete(child);
		}
		f.delete();
	}

	private static boolean isSymlink(File f) throws IOException {
		return org.apache.commons.io.FileUtils.isSymlink(f);
	}

	public interface Callback {

		void processingFile(File f);
	}
}
