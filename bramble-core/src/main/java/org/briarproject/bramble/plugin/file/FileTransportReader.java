package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportConnectionReader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

@NotNullByDefault
class FileTransportReader implements TransportConnectionReader {

	private static final Logger LOG =
			Logger.getLogger(FileTransportReader.class.getName());

	private final File file;
	private final InputStream in;
	private final FilePlugin plugin;

	FileTransportReader(File file, InputStream in, FilePlugin plugin) {
		this.file = file;
		this.in = in;
		this.plugin = plugin;
	}

	@Override
	public InputStream getInputStream() {
		return in;
	}

	@Override
	public void dispose(boolean exception, boolean recognised) {
		try {
			in.close();
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
		if (recognised) {
			file.delete();
			plugin.readerFinished(file);
		}
	}
}
