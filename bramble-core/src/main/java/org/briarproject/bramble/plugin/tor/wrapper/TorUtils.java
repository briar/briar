package org.briarproject.bramble.plugin.tor.wrapper;

import org.briarproject.nullsafety.NotNullByDefault;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.plugin.tor.wrapper.TorWrapper.LOG;

@NotNullByDefault
class TorUtils {

	@SuppressWarnings("CharsetObjectCanBeUsed")
	static final Charset UTF_8 = Charset.forName("UTF-8");

	static String scrubOnion(String onion) {
		// Keep first three characters of onion address
		return onion.substring(0, 3) + "[scrubbed]";
	}

	static void copyAndClose(InputStream in, OutputStream out) {
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

	static void tryToClose(@Nullable Closeable c, Logger logger, Level level) {
		try {
			if (c != null) c.close();
		} catch (IOException e) {
			logException(logger, level, e);
		}
	}

	static void tryToClose(@Nullable Socket s, Logger logger, Level level) {
		try {
			if (s != null) s.close();
		} catch (IOException e) {
			logException(logger, level, e);
		}
	}

	private static void logException(Logger logger, Level level, Throwable t) {
		if (logger.isLoggable(level)) logger.log(level, t.toString(), t);
	}
}
