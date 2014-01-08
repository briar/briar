package org.briarproject.plugins.file;

import static java.util.logging.Level.WARNING;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;

import org.briarproject.api.plugins.simplex.SimplexTransportWriter;

class FileTransportWriter implements SimplexTransportWriter {

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

	public int getMaxFrameLength() {
		return plugin.getMaxFrameLength();
	}

	public long getMaxLatency() {
		return plugin.getMaxLatency();
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
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
		if(exception) file.delete();
		else plugin.writerFinished(file);
	}
}
