package net.sf.briar.plugins.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.simplex.SimplexPlugin;
import net.sf.briar.api.plugins.simplex.SimplexPluginCallback;
import net.sf.briar.api.plugins.simplex.SimplexTransportReader;
import net.sf.briar.api.plugins.simplex.SimplexTransportWriter;
import net.sf.briar.api.transport.TransportConstants;
import net.sf.briar.util.StringUtils;

import org.apache.commons.io.FileSystemUtils;

public abstract class FilePlugin implements SimplexPlugin {

	private static final Logger LOG =
			Logger.getLogger(FilePlugin.class.getName());

	protected final Executor pluginExecutor;
	protected final SimplexPluginCallback callback;

	protected volatile boolean running = false;

	private final Object listenerLock = new Object();

	private FileListener listener = null; // Locking: listenerLock

	protected abstract File chooseOutputDirectory();
	protected abstract Collection<File> findFilesByName(String filename);
	protected abstract void writerFinished(File f);
	protected abstract void readerFinished(File f);

	protected FilePlugin(@PluginExecutor Executor pluginExecutor,
			SimplexPluginCallback callback) {
		this.pluginExecutor = pluginExecutor;
		this.callback = callback;
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
			if(capacity < TransportConstants.MIN_CONNECTION_LENGTH) return null;
			OutputStream out = new FileOutputStream(f);
			return new FileTransportWriter(f, out, capacity, this);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
			f.delete();
			return null;
		}
	}

	private long getCapacity(String path) throws IOException {
		return FileSystemUtils.freeSpaceKb(path) * 1024L;
	}

	protected void createReaderFromFile(final File f) {
		if(!running) return;
		pluginExecutor.execute(new ReaderCreator(f));
	}

	public SimplexTransportWriter sendInvitation(PseudoRandom r, long timeout) {
		if(!running) return null;
		return createWriter(createInvitationFilename(r, false));
	}

	public SimplexTransportReader acceptInvitation(PseudoRandom r,
			long timeout) {
		if(!running) return null;
		String filename = createInvitationFilename(r, false);
		return createInvitationReader(filename, timeout);
	}

	public SimplexTransportWriter sendInvitationResponse(PseudoRandom r,
			long timeout) {
		if(!running) return null;
		return createWriter(createInvitationFilename(r, true));
	}

	public SimplexTransportReader acceptInvitationResponse(PseudoRandom r,
			long timeout) {
		if(!running) return null;
		String filename = createInvitationFilename(r, true);
		return createInvitationReader(filename, timeout);
	}

	private SimplexTransportReader createInvitationReader(String filename,
			long timeout) {
		Collection<File> files;
		// FIXME: Avoid making alien calls with a lock held
		synchronized(listenerLock) {
			// Find any matching files that have already arrived
			files = findFilesByName(filename);
			if(files.isEmpty()) {
				// Wait for a matching file to arrive
				listener = new FileListener(filename, timeout);
				File f;
				try {
					f = listener.waitForFile();
					if(f != null) files.add(f);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(Level.INFO))
						LOG.info("Interrupted while waiting for file");
					Thread.currentThread().interrupt();
				}
				listener = null;
			}
		}
		// Return the first match that can be opened
		for(File f : files) {
			try {
				FileInputStream in = new FileInputStream(f);
				return new FileTransportReader(f, in, FilePlugin.this);
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
			}
		}
		return null;
	}

	private String createInvitationFilename(PseudoRandom r, boolean response) {
		String digits = StringUtils.toHexString(r.nextBytes(3));
		return String.format("%c%s.dat", response ? 'b' : 'a', digits);
	}

	// Package access for testing
	boolean isPossibleInvitationFilename(String filename) {
		return filename.toLowerCase().matches("[ab][0-9a-f]{6}.dat");
	}

	private class ReaderCreator implements Runnable {

		private final File file;

		private ReaderCreator(File file) {
			this.file = file;
		}

		public void run() {
			String filename = file.getName();
			if(isPossibleInvitationFilename(filename)) {
				// FIXME: Avoid making alien calls with a lock held
				synchronized(listenerLock) {
					if(listener != null) listener.addFile(file);
				}
			}
			if(isPossibleConnectionFilename(file.getName())) {
				try {
					FileInputStream in = new FileInputStream(file);
					callback.readerCreated(new FileTransportReader(file, in,
							FilePlugin.this));
				} catch(IOException e) {
					if(LOG.isLoggable(Level.WARNING))
						LOG.warning(e.toString());
				}
			}
		}
	}
}
