package net.sf.briar.plugins.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.transport.InvalidConfigException;
import net.sf.briar.api.transport.InvalidPropertiesException;
import net.sf.briar.api.transport.TransportConstants;
import net.sf.briar.api.transport.batch.BatchTransportCallback;
import net.sf.briar.api.transport.batch.BatchTransportPlugin;
import net.sf.briar.api.transport.batch.BatchTransportReader;
import net.sf.briar.api.transport.batch.BatchTransportWriter;
import net.sf.briar.plugins.AbstractPlugin;

import org.apache.commons.io.FileSystemUtils;

abstract class FilePlugin extends AbstractPlugin
implements BatchTransportPlugin {

	private static final Logger LOG =
		Logger.getLogger(FilePlugin.class.getName());

	protected abstract File chooseOutputDirectory();
	protected abstract void writerFinished(File f);
	protected abstract void readerFinished(File f);

	protected FilePlugin(Executor executor) {
		super(executor);
	}

	public synchronized void start(Map<String, String> localProperties,
			Map<ContactId, Map<String, String>> remoteProperties,
			Map<String, String> config, BatchTransportCallback callback)
	throws InvalidPropertiesException, InvalidConfigException, IOException {
		super.start(localProperties, remoteProperties, config);
		this.callback = callback;
	}

	public BatchTransportReader createReader(ContactId c) {
		return null;
	}

	public BatchTransportWriter createWriter(ContactId c) {
		synchronized(this) {
			if(!started) return null;
		}
		File dir = chooseOutputDirectory();
		if(dir == null || !dir.exists() || !dir.isDirectory()) return null;
		File f = new File(dir, createFilename());
		try {
			long capacity = getCapacity(dir.getPath());
			if(capacity < TransportConstants.MIN_CONNECTION_LENGTH) return null;
			OutputStream out = new FileOutputStream(f);
			return new FileTransportWriter(f, out, capacity, this);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			f.delete();
			return null;
		}
	}

	private String createFilename() {
		StringBuilder s = new StringBuilder(12);
		for(int i = 0; i < 8; i++) s.append((char) ('a' + Math.random() * 26));
		s.append(".dat");
		return s.toString();
	}

	private long getCapacity(String path) throws IOException {
		return FileSystemUtils.freeSpaceKb(path) * 1024L;
	}

	protected synchronized void createReaderFromFile(final File f) {
		if(!started) return;
		executor.execute(new ReaderCreator(f));
	}

	// Package access for testing
	boolean isPossibleConnectionFilename(String filename) {
		return filename.toLowerCase().matches("[a-z]{8}\\.dat");
	}

	private class ReaderCreator implements Runnable {

		private final File f;

		private ReaderCreator(File f) {
			this.f = f;
		}

		public void run() {
			if(!isPossibleConnectionFilename(f.getName())) return;
			if(f.length() < TransportConstants.MIN_CONNECTION_LENGTH) return;
			try {
				FileInputStream in = new FileInputStream(f);
				synchronized(FilePlugin.this) {
					if(started) {
						callback.readerCreated(new FileTransportReader(f, in,
								FilePlugin.this));
					}
				}
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
				return;
			}
		}
	}
}
