package net.sf.briar.plugins.file;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.transport.InvalidConfigException;
import net.sf.briar.api.transport.InvalidTransportException;
import net.sf.briar.api.transport.TransportConstants;
import net.sf.briar.api.transport.batch.BatchTransportCallback;
import net.sf.briar.api.transport.batch.BatchTransportPlugin;
import net.sf.briar.api.transport.batch.BatchTransportReader;
import net.sf.briar.api.transport.batch.BatchTransportWriter;

import org.apache.commons.io.FileSystemUtils;

abstract class FilePlugin implements BatchTransportPlugin {

	protected Map<String, String> localProperties = null;
	protected Map<ContactId, Map<String, String>> remoteProperties = null;
	protected Map<String, String> config = null;
	protected BatchTransportCallback callback = null;
	private boolean started = false;

	protected abstract File chooseOutputDirectory();
	protected abstract void writerFinished(File f);

	public synchronized void start(Map<String, String> localProperties,
			Map<ContactId, Map<String, String>> remoteProperties,
			Map<String, String> config, BatchTransportCallback callback)
	throws InvalidTransportException, InvalidConfigException {
		if(started) throw new IllegalStateException();
		started = true;
		this.localProperties = localProperties;
		this.remoteProperties = remoteProperties;
		this.config = config;
		this.callback = callback;
	}

	public synchronized void stop() {
		if(!started) throw new IllegalStateException();
		started = false;
	}

	public synchronized void setLocalProperties(Map<String, String> properties)
	throws InvalidTransportException {
		if(!started) throw new IllegalStateException();
		localProperties = properties;
	}

	public synchronized void setRemoteProperties(ContactId c,
			Map<String, String> properties)
	throws InvalidTransportException {
		if(!started) throw new IllegalStateException();
		remoteProperties.put(c, properties);
	}

	public synchronized void setConfig(Map<String, String> config)
	throws InvalidConfigException {
		if(!started) throw new IllegalStateException();
		this.config = config;
	}

	public boolean shouldPoll() {
		return false;
	}

	public int getPollingInterval() {
		return 0;
	}

	public void poll() {
		throw new UnsupportedOperationException();
	}

	public BatchTransportReader createReader(ContactId c) {
		return null;
	}

	public BatchTransportWriter createWriter(ContactId c) {
		if(!started) throw new IllegalStateException();
		File dir = chooseOutputDirectory();
		if(dir == null || !dir.exists() || !dir.isDirectory()) return null;
		File f = new File(dir, createFilename());
		try {
			long capacity = getCapacity(f.getAbsolutePath());
			if(capacity < TransportConstants.MIN_CONNECTION_LENGTH) return null;
			OutputStream out = new FileOutputStream(f);
			return new FileTransportWriter(f, out, capacity, this);
		} catch(IOException e) {
			f.delete();
			return null;
		}
	}

	protected String createFilename() {
		StringBuilder s = new StringBuilder(12);
		for(int i = 0; i < 8; i++) s.append((char) ('a' + Math.random() * 26));
		s.append(".dat");
		return s.toString();
	}

	protected long getCapacity(String path) throws IOException {
		return FileSystemUtils.freeSpaceKb(path) * 1024L;
	}
}
