package org.briarproject.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class IoUtils {

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

	public static void copy(InputStream in, OutputStream out)
			throws IOException {
		byte[] buf = new byte[4096];
		try {
			try {
				while (true) {
					int read = in.read(buf);
					if (read == -1) break;
					out.write(buf, 0, read);
				}
				out.flush();
			} finally {
				in.close();
			}
		} finally {
			out.close();
		}
	}

}
