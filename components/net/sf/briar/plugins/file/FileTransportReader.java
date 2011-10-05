package net.sf.briar.plugins.file;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import net.sf.briar.api.transport.batch.BatchTransportReader;

class FileTransportReader implements BatchTransportReader {

	private final File file;
	private final InputStream in;
	private final FilePlugin plugin;

	private boolean streamInUse = false;

	FileTransportReader(File file, InputStream in, FilePlugin plugin) {
		this.file = file;
		this.in = in;
		this.plugin = plugin;
	}

	public InputStream getInputStream() {
		streamInUse = true;
		return in;
	}

	public void finish() throws IOException {
		streamInUse = false;
		plugin.readerFinished(file);
	}

	public void dispose() throws IOException {
		if(streamInUse) in.close();
		file.delete();
	}
}
