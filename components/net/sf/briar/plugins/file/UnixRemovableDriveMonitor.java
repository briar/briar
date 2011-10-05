package net.sf.briar.plugins.file;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.contentobjects.jnotify.JNotify;
import net.contentobjects.jnotify.JNotifyListener;

abstract class UnixRemovableDriveMonitor implements RemovableDriveMonitor,
JNotifyListener {

	private final List<Integer> watches = new ArrayList<Integer>();

	private boolean started = false;
	private Callback callback = null;

	protected abstract String[] getPathsToWatch();

	public synchronized void start(Callback callback) throws IOException {
		if(started) throw new IllegalStateException();
		started = true;
		this.callback = callback;
		int mask = JNotify.FILE_CREATED;
		for(String path : getPathsToWatch()) {
			if(new File(path).exists())
				watches.add(JNotify.addWatch(path, mask, false, this));
		}
	}

	public synchronized void stop() throws IOException {
		if(!started) throw new IllegalStateException();
		started = false;
		callback = null;
		for(Integer w : watches) JNotify.removeWatch(w);
		watches.clear();
	}

	public void fileCreated(int wd, String rootPath, String name) {
		synchronized(this) {
			if(!started) throw new IllegalStateException();
			callback.driveInserted(new File(rootPath + "/" + name));
		}
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
