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
import net.sf.briar.plugins.AbstractPlugin;

abstract class SocketPlugin extends AbstractPlugin
implements StreamTransportPlugin {

	// These fields should be accessed with this's lock held
	protected StreamTransportCallback callback = null;
	protected ServerSocket socket = null;

	// These methods should be called with this's lock held and started == true
	protected abstract SocketAddress getLocalSocketAddress();
	protected abstract SocketAddress getSocketAddress(ContactId c);
	protected abstract void setLocalSocketAddress(SocketAddress s);
	protected abstract Socket createClientSocket() throws IOException;
	protected abstract ServerSocket createServerSocket() throws IOException;

	protected SocketPlugin(Executor executor) {
		super(executor);
	}

	public synchronized void start(Map<String, String> localProperties,
			Map<ContactId, Map<String, String>> remoteProperties,
			Map<String, String> config, StreamTransportCallback callback)
	throws InvalidPropertiesException, InvalidConfigException {
		super.start(localProperties, remoteProperties, config);
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
				if(socket == null) return;
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
		super.stop();
		if(socket != null) {
			socket.close();
			socket = null;
		}
	}

	public synchronized void setLocalProperties(Map<String, String> properties)
	throws InvalidPropertiesException {
		super.setLocalProperties(properties);
		// Close and reopen the socket if its address has changed
		if(socket != null) {
			SocketAddress addr = socket.getLocalSocketAddress();
			if(!getLocalSocketAddress().equals(addr)) {
				try {
					socket.close();
				} catch(IOException e) {
					// FIXME: Logging
				}
				socket = null;
				executor.execute(createBinder());
			}
		}
	}

	public synchronized void poll() {
		// Subclasses may not support polling
		if(!shouldPoll()) throw new UnsupportedOperationException();
		if(!started) return;
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
		synchronized(this) {
			if(!started) return null;
		}
		return createAndConnectSocket(c);
	}
}
