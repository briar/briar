package org.briarproject.plugins.file;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.contentobjects.jnotify.JNotify;
import net.contentobjects.jnotify.JNotifyListener;

abstract class UnixRemovableDriveMonitor implements RemovableDriveMonitor,
JNotifyListener {

	private static boolean triedLoad = false;
	private static Throwable loadError = null;

	private final List<Integer> watches = new ArrayList<Integer>();

	private boolean started = false;
	private Callback callback = null;

	protected abstract String[] getPathsToWatch();
	
	//TODO: rationalise this in a further refactor
	private final Lock synchLock = new ReentrantLock();
	private static final Lock staticSynchLock = new ReentrantLock();
	
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

	public static void checkEnabled() throws IOException {
		staticSynchLock.lock();
		try {
			if(!triedLoad) {
				loadError = tryLoad();
				triedLoad = true;
			}
			if(loadError != null) throw new IOException(loadError.toString());
		} 
		finally{
			staticSynchLock.unlock();
		}
	}

	public void start(Callback callback) throws IOException {
		checkEnabled();
		List<Integer> watches = new ArrayList<Integer>();
		int mask = JNotify.FILE_CREATED;
		for(String path : getPathsToWatch()) {
			if(new File(path).exists())
				watches.add(JNotify.addWatch(path, mask, false, this));
		}
			synchLock.lock();
			try {
				assert !started;
				assert this.callback == null;
				started = true;
				this.callback = callback;
				this.watches.addAll(watches);
			} 
			finally{
				synchLock.unlock();
			}
	}

	public void stop() throws IOException {
		checkEnabled();
		List<Integer> watches;
			synchLock.lock();
			try {
				assert started;
				assert callback != null;
				started = false;
				callback = null;
				watches = new ArrayList<Integer>(this.watches);
				this.watches.clear();
			} 
			finally{
				synchLock.unlock();
			}
		for(Integer w : watches) JNotify.removeWatch(w);
	}

	public void fileCreated(int wd, String rootPath, String name) {
		Callback callback;
			synchLock.lock();
			try {
				callback = this.callback;
			} 
			finally{
				synchLock.unlock();
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
