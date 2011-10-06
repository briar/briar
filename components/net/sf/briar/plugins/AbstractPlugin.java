package net.sf.briar.plugins;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.transport.InvalidConfigException;
import net.sf.briar.api.transport.InvalidPropertiesException;
import net.sf.briar.api.transport.TransportPlugin;
import net.sf.briar.api.transport.batch.BatchTransportCallback;

public abstract class AbstractPlugin implements TransportPlugin {

	protected final Executor executor;

	protected Map<String, String> localProperties = null;
	protected Map<ContactId, Map<String, String>> remoteProperties = null;
	protected Map<String, String> config = null;
	protected BatchTransportCallback callback = null;
	protected boolean started = false;

	protected AbstractPlugin(Executor executor) {
		this.executor = executor;
	}

	protected void start(Map<String, String> localProperties,
			Map<ContactId, Map<String, String>> remoteProperties,
			Map<String, String> config) throws InvalidPropertiesException,
			InvalidConfigException {
		if(started) throw new IllegalStateException();
		started = true;
		this.localProperties = Collections.unmodifiableMap(localProperties);
		// Copy the remoteProperties map to make its values unmodifiable
		// Copy the remoteProperties map to make its values unmodifiable
		int size = remoteProperties.size();
		Map<ContactId, Map<String, String>> m =
			new HashMap<ContactId, Map<String, String>>(size);
		for(Entry<ContactId, Map<String, String>> e
				: remoteProperties.entrySet()) {
			m.put(e.getKey(), Collections.unmodifiableMap(e.getValue()));
		}
		this.remoteProperties = m;
		this.config = Collections.unmodifiableMap(config);
	}

	public synchronized void stop() throws IOException {
		if(!started) throw new IllegalStateException();
		started = false;
	}

	public synchronized void setLocalProperties(Map<String, String> properties)
	throws InvalidPropertiesException {
		if(!started) throw new IllegalStateException();
		localProperties = Collections.unmodifiableMap(properties);
	}

	public synchronized void setRemoteProperties(ContactId c,
			Map<String, String> properties)
	throws InvalidPropertiesException {
		if(!started) throw new IllegalStateException();
		remoteProperties.put(c, Collections.unmodifiableMap(properties));
	}

	public synchronized void setConfig(Map<String, String> config)
	throws InvalidConfigException {
		if(!started) throw new IllegalStateException();
		this.config = Collections.unmodifiableMap(config);
	}
}
