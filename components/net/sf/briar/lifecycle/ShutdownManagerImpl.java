package net.sf.briar.lifecycle;

import java.util.HashMap;
import java.util.Map;

import net.sf.briar.api.lifecycle.ShutdownManager;

class ShutdownManagerImpl implements ShutdownManager {

	protected final Map<Integer, Thread> hooks; // Locking: this

	private int nextHandle = 0; // Locking: this

	ShutdownManagerImpl() {
		hooks = new HashMap<Integer, Thread>();
	}

	public synchronized int addShutdownHook(Runnable runnable) {
		int handle = nextHandle++;
		Thread hook = new Thread(runnable);
		hooks.put(handle, hook);
		Runtime.getRuntime().addShutdownHook(hook);
		return handle;
	}

	public synchronized boolean removeShutdownHook(int handle) {
		Thread hook = hooks.remove(handle);
		if(hook == null) return false;
		else return Runtime.getRuntime().removeShutdownHook(hook);
	}
}
