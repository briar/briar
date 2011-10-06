package net.sf.briar.plugins.socket;

import java.io.IOException;
import java.net.ServerSocket;
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
	protected ServerSocket socket = null;

	private volatile boolean started = false;

	// These methods should be called with this's lock held and started == true
	protected abstract SocketAddress getLocalSocketAddress();
	protected abstract SocketAddress getSocketAddress(ContactId c);
	protected abstract void setLocalSocketAddress(SocketAddress s);
	protected abstract Socket createClientSocket() throws IOException;
	protected abstract ServerSocket createServerSocket() throws IOException;

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

	private Runnable createBinder() {
		return new Runnable() {
			public void run() {
				bind();
			}
		};
	}

	private void bind() {
		SocketAddress addr;
		ServerSocket ss;
		try {
			synchronized(this) {
				if(!started) return;
				addr = getLocalSocketAddress();
				ss = createServerSocket();
			}
			if(addr == null || ss == null) return;
			ss.bind(addr);
		} catch(IOException e) {
			// FIXME: Logging
			return;
		}
		synchronized(this) {
			if(!started) return;
			socket = ss;
			setLocalSocketAddress(ss.getLocalSocketAddress());
			startListener();
		}
	}

	private void startListener() {
		new Thread() {
			@Override
			public void run() {
				listen();
			}
		}.start();
	}

	private void listen() {
		while(true) {
			ServerSocket ss;
			Socket s;
			synchronized(this) {
				if(!started) return;
				ss = socket;
			}
			try {
				s = ss.accept();
			} catch(IOException e) {
				// FIXME: Logging
				return;
			}
			synchronized(this) {
				if(!started) {
					try {
						s.close();
					} catch(IOException e) {
						// FIXME: Logging
					}
					return;
				}
				SocketTransportConnection conn =
					new SocketTransportConnection(s);
				callback.incomingConnectionCreated(conn);
			}
		}
	}

	public synchronized void stop() throws IOException {
		if(!started) throw new IllegalStateException();
		started = false;
		if(socket != null) socket.close();
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

	private Runnable createConnector(final ContactId c) {
		return new Runnable() {
			public void run() {
				connect(c);
			}
		};
	}

	private StreamTransportConnection connect(ContactId c) {
		StreamTransportConnection conn = createAndConnectSocket(c);
		if(conn != null) {
			synchronized(this) {
				if(started) callback.outgoingConnectionCreated(c, conn);
			}
		}
		return conn;
	}

	private StreamTransportConnection createAndConnectSocket(ContactId c) {
		SocketAddress addr;
		Socket s;
		try {
			synchronized(this) {
				if(!started) return null;
				addr = getSocketAddress(c);
				s = createClientSocket();
			}
			if(addr == null || s == null) return null;
			s.connect(addr);
		} catch(IOException e) {
			return null;
		}
		return new SocketTransportConnection(s);
	}

	public StreamTransportConnection createConnection(ContactId c) {
		return started ? createAndConnectSocket(c) : null;
	}
}
