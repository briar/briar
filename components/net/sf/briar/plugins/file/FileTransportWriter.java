package net.sf.briar.plugins.file;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.transport.BatchTransportWriter;

class FileTransportWriter implements BatchTransportWriter {

	private static final Logger LOG =
		Logger.getLogger(FileTransportWriter.class.getName());

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

	public boolean shouldFlush() {
		return false;
	}

	public void dispose(boolean exception) {
		try {
			out.close();
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
		}
		if(exception) file.delete();
		else plugin.writerFinished(file);
	}
}
