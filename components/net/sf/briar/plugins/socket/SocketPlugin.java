package net.sf.briar.plugins.socket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.StreamPlugin;
import net.sf.briar.api.plugins.StreamPluginCallback;
import net.sf.briar.api.transport.StreamTransportConnection;

abstract class SocketPlugin implements StreamPlugin {

	private static final Logger LOG =
		Logger.getLogger(SocketPlugin.class.getName());

	protected final Executor pluginExecutor;
	protected final StreamPluginCallback callback;

	private final long pollingInterval;

	protected boolean running = false; // Locking: this
	protected ServerSocket socket = null; // Locking: this

	protected abstract void setLocalSocketAddress(SocketAddress s);

	protected abstract Socket createClientSocket() throws IOException;
	protected abstract ServerSocket createServerSocket() throws IOException;
	protected abstract SocketAddress getLocalSocketAddress();
	protected abstract SocketAddress getRemoteSocketAddress(ContactId c);

	protected SocketPlugin(@PluginExecutor Executor pluginExecutor,
			StreamPluginCallback callback, long pollingInterval) {
		this.pluginExecutor = pluginExecutor;
		this.callback = callback;
		this.pollingInterval = pollingInterval;
	}

	public void start() throws IOException {
		synchronized(this) {
			running = true;
		}
		pluginExecutor.execute(new Runnable() {
			public void run() {
				bind();
			}
		});
	}

	private void bind() {
		SocketAddress addr;
		ServerSocket ss = null;
		try {
			addr = getLocalSocketAddress();
			ss = createServerSocket();
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
			return;
		}
		if(addr == null || ss == null) return;
		try {
			ss.bind(addr);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
			tryToClose(ss);
			return;
		}
		synchronized(this) {
			if(!running) {
				tryToClose(ss);
				return;
			}
			socket = ss;
		}
		if(LOG.isLoggable(Level.INFO)) {
			LOG.info("Listening on " + ss.getInetAddress().getHostAddress()
					+ ":" + ss.getLocalPort());
		}
		setLocalSocketAddress(ss.getLocalSocketAddress());
		acceptContactConnections(ss);
	}

	private void tryToClose(ServerSocket ss) {
		try {
			ss.close();
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
		}
	}

	private void acceptContactConnections(ServerSocket ss) {
		while(true) {
			Socket s;
			try {
				s = ss.accept();
			} catch(IOException e) {
				// This is expected when the socket is closed
				if(LOG.isLoggable(Level.INFO)) LOG.info(e.toString());
				tryToClose(ss);
				return;
			}
			SocketTransportConnection conn = new SocketTransportConnection(s);
			callback.incomingConnectionCreated(conn);
			synchronized(this) {
				if(!running) return;
			}
		}
	}

	public synchronized void stop() throws IOException {
		running = false;
		if(socket != null) {
			socket.close();
			socket = null;
		}
	}

	public boolean shouldPoll() {
		return true;
	}

	public long getPollingInterval() {
		return pollingInterval;
	}

	public void poll(Collection<ContactId> connected) {
		synchronized(this) {
			if(!running) return;
		}
		Map<ContactId, TransportProperties> remote =
			callback.getRemoteProperties();
		for(final ContactId c : remote.keySet()) {
			if(connected.contains(c)) continue;
			pluginExecutor.execute(new Runnable() {
				public void run() {
					connectAndCallBack(c);
				}
			});
		}
	}

	private void connectAndCallBack(ContactId c) {
		StreamTransportConnection conn = createConnection(c);
		if(conn != null) callback.outgoingConnectionCreated(c, conn);
	}

	public StreamTransportConnection createConnection(ContactId c) {
		synchronized(this) {
			if(!running) return null;
		}
		SocketAddress addr = getRemoteSocketAddress(c);
		try {
			Socket s = createClientSocket();
			if(addr == null || s == null) return null;
			s.connect(addr);
			return new SocketTransportConnection(s);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.INFO)) LOG.info(e.toString());
			return null;
		}
	}
}
