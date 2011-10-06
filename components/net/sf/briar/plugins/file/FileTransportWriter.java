package net.sf.briar.plugins.file;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import net.sf.briar.api.transport.batch.BatchTransportWriter;

class FileTransportWriter implements BatchTransportWriter {

	private final File file;
	private final OutputStream out;
	private final long capacity;
	private final FilePlugin plugin;

	FileTransportWriter(File file, OutputStream out, long capacity,
			FilePlugin plugin) {
		this.file = file;
		this.out = out;
		this.capacity = capacity;
		this.plugin = plugin;
	}

	public long getCapacity() {
		return capacity;
	}

	public OutputStream getOutputStream() {
		return out;
	}

	public void dispose(boolean success) throws IOException {
		try {
			out.close();
			if(success) plugin.writerFinished(file);
		} finally {
			file.delete();
		}
	}
}
