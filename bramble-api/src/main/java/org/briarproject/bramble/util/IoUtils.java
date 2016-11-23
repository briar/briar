package org.briarproject.bramble.util;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.annotation.Nullable;

@NotNullByDefault
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

	public static void copyAndClose(InputStream in, OutputStream out)
			throws IOException {
		byte[] buf = new byte[4096];
		try {
			while (true) {
				int read = in.read(buf);
				if (read == -1) break;
				out.write(buf, 0, read);
			}
			in.close();
			out.flush();
			out.close();
		} catch (IOException e) {
			tryToClose(in);
			tryToClose(out);
		}
	}

	private static void tryToClose(@Nullable Closeable c) {
		try {
			if (c != null) c.close();
		} catch (IOException e) {
			// We did our best
		}
	}

	public static void read(InputStream in, byte[] b) throws IOException {
		int offset = 0;
		while (offset < b.length) {
			int read = in.read(b, offset, b.length - offset);
			if (read == -1) throw new EOFException();
			offset += read;
		}
	}
}
