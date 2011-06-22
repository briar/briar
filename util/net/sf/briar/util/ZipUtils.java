package net.sf.briar.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipUtils {

	/**
	 * Copies the given file to the given zip, using the given path for the
	 * zip entry.
	 */
	public static void copyToZip(String path, File file, ZipOutputStream zip)
	throws IOException {
		assert file.isFile();
		zip.putNextEntry(new ZipEntry(path));
		FileInputStream in = new FileInputStream(file);
		byte[] buf = new byte[1024];
		int i;
		while((i = in.read(buf, 0, buf.length)) != -1) zip.write(buf, 0, i);
		in.close();
		zip.closeEntry();
	}

	/**
	 * Copies the given directory to the given zip recursively, using the
	 * given path in place of the directory's name as the parent of all the zip
	 * entries. If the callback is not null it's called once for each file
	 * added.
	 */
	public static void copyToZipRecursively(String path, File dir,
			ZipOutputStream zip, Callback callback) throws IOException {
		assert dir.isDirectory();
		for(File child : dir.listFiles()) {
			String childPath = extendPath(path, child.getName());
			if(child.isDirectory()) {
				copyToZipRecursively(childPath, child, zip, callback);
			} else {
				if(callback != null) callback.processingFile(child);
				copyToZip(childPath, child, zip);
			}
		}
	}

	private static String extendPath(String path, String name) {
		if(path == null || path.equals("")) return name;
		else return path + "/" + name;
	}

	/**
	 * Unzips the given stream to the given directory, skipping any zip entries
	 * that don't match the given regex. If the callback is not null it's
	 * called once for each file extracted.
	 */
	public static void unzipStream(InputStream in, File dir, String regex,
			Callback callback) throws IOException {
		String path = dir.getCanonicalPath();
		ZipInputStream zip = new ZipInputStream(in);
		byte[] buf = new byte[1024];
		ZipEntry entry;
		while((entry = zip.getNextEntry()) != null) {
			String name = entry.getName();
			if(name.matches(regex)) {
				File file = new File(path + "/" + name);
				if(callback != null) callback.processingFile(file);
				if(entry.isDirectory()) {
					file.mkdirs();
				} else {
					file.getParentFile().mkdirs();
					FileOutputStream out = new FileOutputStream(file);
					int i;
					while((i = zip.read(buf, 0, buf.length)) > 0) {
						out.write(buf, 0, i);
					}
					out.flush();
					out.close();
				}
			}
			zip.closeEntry();
		}
		zip.close();
	}

	public interface Callback {

		void processingFile(File f);
	}
}