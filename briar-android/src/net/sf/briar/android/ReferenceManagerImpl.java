package net.sf.briar.android;

import static java.util.logging.Level.INFO;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import net.sf.briar.api.android.ReferenceManager;

// This class is not thread-safe.
class ReferenceManagerImpl implements ReferenceManager {

	private static final Logger LOG =
			Logger.getLogger(ReferenceManagerImpl.class.getName());

	private final Map<Class<?>, Map<Long, Object>> outerMap =
			new HashMap<Class<?>, Map<Long, Object>>();

	private long nextHandle = 0;

	public <T> T getReference(long handle, Class<T> c) {
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

	public <T> long putReference(T reference, Class<T> c) {
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

	public <T> T removeReference(long handle, Class<T> c) {
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
}
