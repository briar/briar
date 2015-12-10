package org.briarproject.util;

import java.io.File;

public class FileUtils {

	public static void deleteFileOrDir(File f) {
		if (f.isFile()) {
			f.delete();
		} else if (f.isDirectory()) {
			for (File child : f.listFiles()) deleteFileOrDir(child);
			f.delete();
		}
	}
}
