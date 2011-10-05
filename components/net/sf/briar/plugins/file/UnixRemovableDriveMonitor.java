package net.sf.briar.plugins.file;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.contentobjects.jnotify.JNotify;
import net.contentobjects.jnotify.JNotifyListener;

abstract class UnixRemovableDriveMonitor implements RemovableDriveMonitor,
JNotifyListener {

	private final List<Integer> watches = new ArrayList<Integer>();
	private final LinkedList<File> inserted = new LinkedList<File>();

	private boolean started = false, stopped = false;

	protected abstract String[] getPathsToWatch();

	public synchronized void start() throws IOException {
		if(started || stopped) throw new IllegalStateException();
		started = true;
		int mask = JNotify.FILE_CREATED;
		for(String path : getPathsToWatch()) {
			if(new File(path).exists())
				watches.add(JNotify.addWatch(path, mask, false, this));
		}
	}

	public synchronized File waitForInsertion() throws IOException {
		if(!started || stopped) throw new IllegalStateException();
		while(inserted.isEmpty()) {
			try {
				wait();
			} catch(InterruptedException ignored) {}
		}
		return inserted.poll();
	}

	public synchronized void stop() throws IOException {
		if(!started || stopped) throw new IllegalStateException();
		stopped = true;
		for(Integer w : watches) JNotify.removeWatch(w);
	}

	public void fileCreated(int wd, String rootPath, String name) {
		synchronized(this) {
			inserted.add(new File(rootPath + "/" + name));
			notifyAll();
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
