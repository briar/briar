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

	private boolean streamInUse = false;

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
		streamInUse = true;
		return out;
	}

	public void finish() {
		streamInUse = false;
		plugin.writerFinished(file);
	}

	public void dispose() throws IOException {
		if(streamInUse) out.close();
		file.delete();
	}
}
