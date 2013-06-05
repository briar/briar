package net.sf.briar.plugins.file;

import static java.util.logging.Level.WARNING;
import static net.sf.briar.api.transport.TransportConstants.MIN_CONNECTION_LENGTH;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.plugins.simplex.SimplexPlugin;
import net.sf.briar.api.plugins.simplex.SimplexPluginCallback;
import net.sf.briar.api.plugins.simplex.SimplexTransportReader;
import net.sf.briar.api.plugins.simplex.SimplexTransportWriter;

import org.apache.commons.io.FileSystemUtils;

public abstract class FilePlugin implements SimplexPlugin {

	private static final Logger LOG =
			Logger.getLogger(FilePlugin.class.getName());

	protected final Executor pluginExecutor;
	protected final SimplexPluginCallback callback;
	protected final int maxFrameLength;
	protected final long maxLatency;

	protected volatile boolean running = false;

	protected abstract File chooseOutputDirectory();
	protected abstract Collection<File> findFilesByName(String filename);
	protected abstract void writerFinished(File f);
	protected abstract void readerFinished(File f);

	protected FilePlugin(Executor pluginExecutor,
			SimplexPluginCallback callback, int maxFrameLength,
			long maxLatency) {
		this.pluginExecutor = pluginExecutor;
		this.callback = callback;
		this.maxFrameLength = maxFrameLength;
		this.maxLatency = maxLatency;
	}

	public int getMaxFrameLength() {
		return maxFrameLength;
	}

	public long getMaxLatency() {
		return maxLatency;
	}

	public SimplexTransportReader createReader(ContactId c) {
		return null;
	}

	public SimplexTransportWriter createWriter(ContactId c) {
		if(!running) return null;
		return createWriter(createConnectionFilename());
	}

	private String createConnectionFilename() {
		StringBuilder s = new StringBuilder(12);
		for(int i = 0; i < 8; i++) s.append((char) ('a' + Math.random() * 26));
		s.append(".dat");
		return s.toString();
	}

	// Package access for testing
	boolean isPossibleConnectionFilename(String filename) {
		return filename.toLowerCase().matches("[a-z]{8}\\.dat");
	}

	private SimplexTransportWriter createWriter(String filename) {
		if(!running) return null;
		File dir = chooseOutputDirectory();
		if(dir == null || !dir.exists() || !dir.isDirectory()) return null;
		File f = new File(dir, filename);
		try {
			long capacity = getCapacity(dir.getPath());
			if(capacity < MIN_CONNECTION_LENGTH) return null;
			OutputStream out = new FileOutputStream(f);
			return new FileTransportWriter(f, out, capacity, this);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			f.delete();
			return null;
		}
	}

	private long getCapacity(String path) throws IOException {
		return FileSystemUtils.freeSpaceKb(path) * 1024;
	}

	protected void createReaderFromFile(final File f) {
		if(!running) return;
		pluginExecutor.execute(new ReaderCreator(f));
	}

	private class ReaderCreator implements Runnable {

		private final File file;

		private ReaderCreator(File file) {
			this.file = file;
		}

		public void run() {
			if(isPossibleConnectionFilename(file.getName())) {
				try {
					FileInputStream in = new FileInputStream(file);
					callback.readerCreated(new FileTransportReader(file, in,
							FilePlugin.this));
				} catch(IOException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		}
	}
}
