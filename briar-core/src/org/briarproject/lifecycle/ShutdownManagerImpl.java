package org.briarproject.lifecycle;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.briarproject.api.lifecycle.ShutdownManager;

class ShutdownManagerImpl implements ShutdownManager {

	private final Lock lock = new ReentrantLock();

	// The following are locking: lock
	protected final Map<Integer, Thread> hooks;
	private int nextHandle = 0;

	ShutdownManagerImpl() {
		hooks = new HashMap<Integer, Thread>();
	}

	public int addShutdownHook(Runnable r) {
		lock.lock();
		try {
			int handle = nextHandle++;
			Thread hook = createThread(r);
			hooks.put(handle, hook);
			Runtime.getRuntime().addShutdownHook(hook);
			return handle;
		} finally {
			lock.unlock();
		}

	}

	protected Thread createThread(Runnable r) {
		return new Thread(r, "ShutdownManager");
	}

	public boolean removeShutdownHook(int handle) {
		lock.lock();
		try {
			Thread hook = hooks.remove(handle);
			if (hook == null) return false;
			else return Runtime.getRuntime().removeShutdownHook(hook);
		} finally {
			lock.unlock();
		}

	}
}
