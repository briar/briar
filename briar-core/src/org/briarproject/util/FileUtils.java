package org.briarproject.util;

import java.io.File;

public class FileUtils {

	public static void deleteFileOrDir(File f) {
		if (f.isFile()) {
			f.delete();
		} else if (f.isDirectory()) {
			File[] children = f.listFiles();
			if (children != null)
				for (File child : children) deleteFileOrDir(child);
			f.delete();
		}
	}
}
