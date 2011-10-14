package net.sf.briar.plugins.file;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.transport.BatchTransportReader;

class FileTransportReader implements BatchTransportReader {

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

	public InputStream getInputStream() {
		return in;
	}

	public void dispose(boolean success) {
		try {
			in.close();
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
		}
		if(success) {
			file.delete();
			plugin.readerFinished(file);
		}
	}
}
