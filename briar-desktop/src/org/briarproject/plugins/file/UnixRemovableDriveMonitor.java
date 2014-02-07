package org.briarproject.plugins.file;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.contentobjects.jnotify.JNotify;
import net.contentobjects.jnotify.JNotifyListener;

abstract class UnixRemovableDriveMonitor implements RemovableDriveMonitor,
JNotifyListener {

	private static boolean triedLoad = false; // Locking: class
	private static Throwable loadError = null; // Locking: class

	// Locking: this
	private final List<Integer> watches = new ArrayList<Integer>();

	private boolean started = false; // Locking: this
	private Callback callback = null; // Locking: this

	protected abstract String[] getPathsToWatch();

	private static Throwable tryLoad() {
		try {
			Class.forName("net.contentobjects.jnotify.JNotify");
			return null;
		} catch(UnsatisfiedLinkError e) {
			return e;
		} catch(ClassNotFoundException e) {
			return e;
		}
	}

	public static synchronized void checkEnabled() throws IOException {
		if(!triedLoad) {
			loadError = tryLoad();
			triedLoad = true;
		}
		if(loadError != null) throw new IOException(loadError.toString());
	}

	public void start(Callback callback) throws IOException {
		checkEnabled();
		List<Integer> watches = new ArrayList<Integer>();
		int mask = JNotify.FILE_CREATED;
		for(String path : getPathsToWatch()) {
			if(new File(path).exists())
				watches.add(JNotify.addWatch(path, mask, false, this));
		}
		synchronized(this) {
			assert !started;
			assert this.callback == null;
			started = true;
			this.callback = callback;
			this.watches.addAll(watches);
		}
	}

	public void stop() throws IOException {
		checkEnabled();
		List<Integer> watches;
		synchronized(this) {
			assert started;
			assert callback != null;
			started = false;
			callback = null;
			watches = new ArrayList<Integer>(this.watches);
			this.watches.clear();
		}
		for(Integer w : watches) JNotify.removeWatch(w);
	}

	public void fileCreated(int wd, String rootPath, String name) {
		Callback callback;
		synchronized(this) {
			callback = this.callback;
		}
		if(callback != null)
			callback.driveInserted(new File(rootPath + "/" + name));
	}

	public void fileDeleted(int wd, String rootPath, String name) {
		throw new UnsupportedOperationException();
	}

	public void fileModified(int wd, String rootPath, String name) {
		throw new UnsupportedOperationException();
	}

	public void fileRenamed(int wd, String rootPath, String oldName,
			String newName) {
		throw new UnsupportedOperationException();
	}
}
