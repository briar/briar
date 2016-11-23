package org.briarproject.bramble.plugin.file;

import net.contentobjects.jnotify.JNotify;
import net.contentobjects.jnotify.JNotifyListener;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
abstract class UnixRemovableDriveMonitor implements RemovableDriveMonitor,
JNotifyListener {

	//TODO: rationalise this in a further refactor
	private static final Lock staticLock = new ReentrantLock();

	// The following are locking: staticLock
	private static boolean triedLoad = false;
	private static Throwable loadError = null;

	private final Lock lock = new ReentrantLock();

	// The following are locking: lock
	private final List<Integer> watches = new ArrayList<>();
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

	private static void checkEnabled() throws IOException {
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

	@Override
	public void start(Callback callback) throws IOException {
		checkEnabled();
		List<Integer> watches = new ArrayList<>();
		int mask = JNotify.FILE_CREATED;
		for (String path : getPathsToWatch()) {
			if (new File(path).exists())
				watches.add(JNotify.addWatch(path, mask, false, this));
		}
		lock.lock();
		try {
			if (started) throw new AssertionError();
			if (this.callback != null) throw new AssertionError();
			started = true;
			this.callback = callback;
			this.watches.addAll(watches);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void stop() throws IOException {
		checkEnabled();
		List<Integer> watches;
		lock.lock();
		try {
			if (!started) throw new AssertionError();
			if (callback == null) throw new AssertionError();
			started = false;
			callback = null;
			watches = new ArrayList<>(this.watches);
			this.watches.clear();
		} finally {
			lock.unlock();
		}
		for (Integer w : watches) JNotify.removeWatch(w);
	}

	@Override
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

	@Override
	public void fileDeleted(int wd, String rootPath, String name) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void fileModified(int wd, String rootPath, String name) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void fileRenamed(int wd, String rootPath, String oldName,
			String newName) {
		throw new UnsupportedOperationException();
	}
}
