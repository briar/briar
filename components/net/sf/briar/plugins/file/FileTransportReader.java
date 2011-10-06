package net.sf.briar.plugins.file;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import net.sf.briar.api.transport.batch.BatchTransportReader;

class FileTransportReader implements BatchTransportReader {

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

	public void dispose(boolean success) throws IOException {
		try {
			in.close();
			if(success) plugin.readerFinished(file);
		} finally {
			file.delete();
		}
	}
}
