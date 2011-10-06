package net.sf.briar.plugins.socket;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.Executor;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.transport.InvalidConfigException;
import net.sf.briar.api.transport.InvalidPropertiesException;
import net.sf.briar.api.transport.stream.StreamTransportCallback;
import net.sf.briar.api.transport.stream.StreamTransportConnection;
import net.sf.briar.api.transport.stream.StreamTransportPlugin;

abstract class SocketPlugin implements StreamTransportPlugin {

	private final Executor executor;

	protected Map<String, String> localProperties = null;
	protected Map<ContactId, Map<String, String>> remoteProperties = null;
	protected Map<String, String> config = null;
	protected StreamTransportCallback callback = null;

	private volatile boolean started = false;

	// These methods should be called with this's lock held and started == true
	protected abstract SocketAddress getLocalSocketAddress();
	protected abstract SocketAddress getSocketAddress(ContactId c);
	protected abstract Socket createClientSocket();
	protected abstract Socket createServerSocket();

	SocketPlugin(Executor executor) {
		this.executor = executor;
	}

	public synchronized void start(Map<String, String> localProperties,
			Map<ContactId, Map<String, String>> remoteProperties,
			Map<String, String> config, StreamTransportCallback callback)
	throws InvalidPropertiesException, InvalidConfigException {
		if(started) throw new IllegalStateException();
		started = true;
		this.localProperties = localProperties;
		this.remoteProperties = remoteProperties;
		this.config = config;
		this.callback = callback;
		executor.execute(createBinder());
	}

	protected Runnable createBinder() {
		return new Runnable() {
			public void run() {
				SocketAddress addr;
				Socket s;
				synchronized(SocketPlugin.this) {
					if(!started) return;
					addr = getLocalSocketAddress();
					s = createServerSocket();
				}
				if(addr == null || s == null) return;
				try {
					s.bind(addr);
				} catch(IOException e) {
					return;
				}
			}
		};
	}

	public synchronized void stop() {
		if(!started) throw new IllegalStateException();
		started = false;
	}

	public synchronized void setLocalProperties(Map<String, String> properties)
	throws InvalidPropertiesException {
		if(!started) throw new IllegalStateException();
		localProperties = properties;
	}

	public synchronized void setRemoteProperties(ContactId c,
			Map<String, String> properties)
	throws InvalidPropertiesException {
		if(!started) throw new IllegalStateException();
		remoteProperties.put(c, properties);
	}

	public synchronized void setConfig(Map<String, String> config)
	throws InvalidConfigException {
		if(!started) throw new IllegalStateException();
		this.config = config;
	}

	public synchronized void poll() {
		if(!shouldPoll()) throw new UnsupportedOperationException();
		if(!started) throw new IllegalStateException();
		for(ContactId c : remoteProperties.keySet()) {
			executor.execute(createConnector(c));
		}
	}

	protected Runnable createConnector(final ContactId c) {
		return new Runnable() {
			public void run() {
				StreamTransportConnection conn = createAndConnectSocket(c);
				if(conn != null) callback.outgoingConnectionCreated(c, conn);
			}
		};
	}

	protected StreamTransportConnection createAndConnectSocket(ContactId c) {
		SocketAddress addr;
		Socket s;
		synchronized(this) {
			if(!started) return null;
			addr = getSocketAddress(c);
			s = createClientSocket();
		}
		if(addr == null || s == null) return null;
		try {
			s.connect(addr);
		} catch(IOException e) {
			return null;
		}
		return new SocketTransportConnection(s);
	}

	public StreamTransportConnection createConnection(ContactId c) {
		if(!started) throw new IllegalStateException();
		return createAndConnectSocket(c);
	}
}
