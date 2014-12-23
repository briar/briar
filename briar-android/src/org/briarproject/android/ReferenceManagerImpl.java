package org.briarproject.android;

import static java.util.logging.Level.INFO;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import org.briarproject.api.android.ReferenceManager;

class ReferenceManagerImpl implements ReferenceManager {

	private static final Logger LOG =
			Logger.getLogger(ReferenceManagerImpl.class.getName());

	private final Map<Class<?>, Map<Long, Object>> outerMap =
			new HashMap<Class<?>, Map<Long, Object>>();

	private long nextHandle = 0; // Locking: this

	private final Lock synchLock = new ReentrantLock();

	public <T> T getReference(long handle, Class<T> c) {
		synchLock.lock();
		try{
			Map<Long, Object> innerMap = outerMap.get(c);
			if(innerMap == null) {
				if(LOG.isLoggable(INFO))
					LOG.info("0 handles for " + c.getName());
				return null;
			}
			if(LOG.isLoggable(INFO))
				LOG.info(innerMap.size() + " handles for " + c.getName());
			Object o = innerMap.get(handle);
			return c.cast(o);
		}
		finally{
			synchLock.unlock();
		}
		
	}

	public <T> long putReference(T reference, Class<T> c) {
		synchLock.lock();
		try{
			Map<Long, Object> innerMap = outerMap.get(c);
			if(innerMap == null) {
				innerMap = new HashMap<Long, Object>();
				outerMap.put(c, innerMap);
			}
			long handle = nextHandle++;
			innerMap.put(handle, reference);
			if(LOG.isLoggable(INFO)) {
				LOG.info(innerMap.size() + " handles for " + c.getName() +
						" after put");
			}
			return handle;
		}
		finally{
			synchLock.unlock();
		}
	}

	public <T> T removeReference(long handle, Class<T> c) {
		synchLock.lock();
		try{
			Map<Long, Object> innerMap = outerMap.get(c);
			if(innerMap == null) return null;
			Object o = innerMap.remove(handle);
			if(innerMap.isEmpty()) outerMap.remove(c);
			if(LOG.isLoggable(INFO)) {
				LOG.info(innerMap.size() + " handles for " + c.getName() +
						" after remove");
			}
			return c.cast(o);
		}
		finally{
			synchLock.unlock();
		}

	}
}
