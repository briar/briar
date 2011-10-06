package net.sf.briar.plugins.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.transport.InvalidConfigException;
import net.sf.briar.api.transport.InvalidPropertiesException;
import net.sf.briar.api.transport.TransportConstants;
import net.sf.briar.api.transport.batch.BatchTransportCallback;
import net.sf.briar.api.transport.batch.BatchTransportPlugin;
import net.sf.briar.api.transport.batch.BatchTransportReader;
import net.sf.briar.api.transport.batch.BatchTransportWriter;

import org.apache.commons.io.FileSystemUtils;

abstract class FilePlugin implements BatchTransportPlugin {

	private final Executor executor;

	protected Map<String, String> localProperties = null;
	protected Map<ContactId, Map<String, String>> remoteProperties = null;
	protected Map<String, String> config = null;
	protected BatchTransportCallback callback = null;

	private volatile boolean started = false;

	protected abstract File chooseOutputDirectory();
	protected abstract void writerFinished(File f);
	protected abstract void readerFinished(File f);

	FilePlugin(Executor executor) {
		this.executor = executor;
	}

	public synchronized void start(Map<String, String> localProperties,
			Map<ContactId, Map<String, String>> remoteProperties,
			Map<String, String> config, BatchTransportCallback callback)
	throws InvalidPropertiesException, InvalidConfigException, IOException {
		if(started) throw new IllegalStateException();
		started = true;
		this.localProperties = Collections.unmodifiableMap(localProperties);
		// Copy the remoteProperties map to make its values unmodifiable
		// Copy the remoteProperties map to make its values unmodifiable
		int size = remoteProperties.size();
		Map<ContactId, Map<String, String>> m =
			new HashMap<ContactId, Map<String, String>>(size);
		for(Entry<ContactId, Map<String, String>> e
				: remoteProperties.entrySet()) {
			m.put(e.getKey(), Collections.unmodifiableMap(e.getValue()));
		}
		this.remoteProperties = m;
		this.config = Collections.unmodifiableMap(config);
		this.callback = callback;
	}

	public synchronized void stop() throws IOException {
		if(!started) throw new IllegalStateException();
		started = false;
	}

	public synchronized void setLocalProperties(Map<String, String> properties)
	throws InvalidPropertiesException {
		if(!started) throw new IllegalStateException();
		localProperties = Collections.unmodifiableMap(properties);
	}

	public synchronized void setRemoteProperties(ContactId c,
			Map<String, String> properties)
	throws InvalidPropertiesException {
		if(!started) throw new IllegalStateException();
		remoteProperties.put(c, Collections.unmodifiableMap(properties));
	}

	public synchronized void setConfig(Map<String, String> config)
	throws InvalidConfigException {
		if(!started) throw new IllegalStateException();
		this.config = Collections.unmodifiableMap(config);
	}

	public BatchTransportReader createReader(ContactId c) {
		return null;
	}

	public BatchTransportWriter createWriter(ContactId c) {
		if(!started) return null;
		File dir = chooseOutputDirectory();
		if(dir == null || !dir.exists() || !dir.isDirectory()) return null;
		File f = new File(dir, createFilename());
		try {
			long capacity = getCapacity(dir.getPath());
			if(capacity < TransportConstants.MIN_CONNECTION_LENGTH) return null;
			OutputStream out = new FileOutputStream(f);
			return new FileTransportWriter(f, out, capacity, this);
		} catch(IOException e) {
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

	protected void createReaderFromFile(final File f) {
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
				return;
			}
		}
	}
}
