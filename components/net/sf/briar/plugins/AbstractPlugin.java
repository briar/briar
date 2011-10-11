package net.sf.briar.plugins;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executor;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.plugins.TransportPlugin;

public abstract class AbstractPlugin implements TransportPlugin {

	protected final Executor executor;

	// These fields should be accessed with this's lock held
	protected TransportProperties localProperties = null;
	protected Map<ContactId, TransportProperties> remoteProperties = null;
	protected TransportConfig config = null;
	protected boolean started = false;

	protected AbstractPlugin(Executor executor) {
		this.executor = executor;
	}

	public synchronized void start(TransportProperties localProperties,
			Map<ContactId, TransportProperties> remoteProperties,
			TransportConfig config) throws IOException {
		if(started) throw new IllegalStateException();
		started = true;
		this.localProperties = localProperties;
		this.remoteProperties = remoteProperties;
		this.config = config;
	}

	public synchronized void stop() throws IOException {
		if(!started) throw new IllegalStateException();
		started = false;
	}

	public synchronized void setLocalProperties(TransportProperties p) {
		if(!started) throw new IllegalStateException();
		localProperties = p;
	}

	public synchronized void setRemoteProperties(ContactId c,
			TransportProperties p) {
		if(!started) throw new IllegalStateException();
		remoteProperties.put(c, p);
	}

	public synchronized void setConfig(TransportConfig c) {
		if(!started) throw new IllegalStateException();
		this.config = c;
	}
}
