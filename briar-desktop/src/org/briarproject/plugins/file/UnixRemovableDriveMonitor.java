package org.briarproject.plugins.file;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.contentobjects.jnotify.JNotify;
import net.contentobjects.jnotify.JNotifyListener;

abstract class UnixRemovableDriveMonitor implements RemovableDriveMonitor,
JNotifyListener {

	//TODO: rationalise this in a further refactor
	private static final Lock staticLock = new ReentrantLock();

	// The following are locking: staticLock
	private static boolean triedLoad = false;
	private static Throwable loadError = null;

	private final Lock lock = new ReentrantLock();

	// The following are locking: lock
	private final List<Integer> watches = new ArrayList<Integer>();
	private boolean started = false;
	private Callback callback = null;

	protected abstract String[] getPathsToWatch();

	private static Throwable tryLoad() {
		try {
			Class.forName("net.contentobjects.jnotify.JNotify");
			return null;
		} catch (UnsatisfiedLinkError e) {
			return e;
		} catch (ClassNotFoundException e) {
			return e;
		}
	}

	public static void checkEnabled() throws IOException {
		staticLock.lock();
		try {
			if (!triedLoad) {
				loadError = tryLoad();
				triedLoad = true;
			}
			if (loadError != null) throw new IOException(loadError.toString());
		} finally {
			staticLock.unlock();
		}
	}

	public void start(Callback callback) throws IOException {
		checkEnabled();
		List<Integer> watches = new ArrayList<Integer>();
		int mask = JNotify.FILE_CREATED;
		for (String path : getPathsToWatch()) {
			if (new File(path).exists())
				watches.add(JNotify.addWatch(path, mask, false, this));
		}
		lock.lock();
		try {
			assert !started;
			assert this.callback == null;
			started = true;
			this.callback = callback;
			this.watches.addAll(watches);
		} finally {
			lock.unlock();
		}
	}

	public void stop() throws IOException {
		checkEnabled();
		List<Integer> watches;
		lock.lock();
		try {
			assert started;
			assert callback != null;
			started = false;
			callback = null;
			watches = new ArrayList<Integer>(this.watches);
			this.watches.clear();
		} finally {
			lock.unlock();
		}
		for (Integer w : watches) JNotify.removeWatch(w);
	}

	public void fileCreated(int wd, String rootPath, String name) {
		Callback callback;
		lock.lock();
		try {
			callback = this.callback;
		} finally {
			lock.unlock();
		}
		if (callback != null)
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
