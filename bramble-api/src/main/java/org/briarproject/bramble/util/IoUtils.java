package org.briarproject.bramble.util;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
public class IoUtils {

	private static final Logger LOG = Logger.getLogger(IoUtils.class.getName());

	public static void deleteFileOrDir(File f) {
		if (f.isFile()) {
			delete(f);
		} else if (f.isDirectory()) {
			File[] children = f.listFiles();
			if (children == null) {
				if (LOG.isLoggable(WARNING)) {
					LOG.warning("Could not list files in "
							+ f.getAbsolutePath());
				}
			} else {
				for (File child : children) deleteFileOrDir(child);
			}
			delete(f);
		}
	}

	private static void delete(File f) {
		if (!f.delete() && LOG.isLoggable(WARNING))
			LOG.warning("Could not delete " + f.getAbsolutePath());
	}

	public static void copyAndClose(InputStream in, OutputStream out) {
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
			tryToClose(in, LOG, WARNING);
			tryToClose(out, LOG, WARNING);
		}
	}

	public static void tryToClose(@Nullable Closeable c, Logger logger,
			Level level) {
		try {
			if (c != null) c.close();
		} catch (IOException e) {
			logException(logger, level, e);
		}
	}

	public static void tryToClose(@Nullable Socket s, Logger logger,
			Level level) {
		try {
			if (s != null) s.close();
		} catch (IOException e) {
			logException(logger, level, e);
		}
	}

	public static void tryToClose(@Nullable ServerSocket ss, Logger logger,
			Level level) {
		try {
			if (ss != null) ss.close();
		} catch (IOException e) {
			logException(logger, level, e);
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

	// Workaround for a bug in Android 7, see
	// https://android-review.googlesource.com/#/c/271775/
	public static InputStream getInputStream(Socket s) throws IOException {
		try {
			return s.getInputStream();
		} catch (NullPointerException e) {
			throw new IOException(e);
		}
	}

	// Workaround for a bug in Android 7, see
	// https://android-review.googlesource.com/#/c/271775/
	public static OutputStream getOutputStream(Socket s) throws IOException {
		try {
			return s.getOutputStream();
		} catch (NullPointerException e) {
			throw new IOException(e);
		}
	}

	public static boolean isNonEmptyDirectory(File f) {
		if (!f.isDirectory()) return false;
		File[] children = f.listFiles();
		return children != null && children.length > 0;
	}
}
