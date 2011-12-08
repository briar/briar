package net.sf.briar.plugins.socket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
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
import net.sf.briar.plugins.AbstractPlugin;

abstract class SocketPlugin extends AbstractPlugin implements StreamPlugin {

	private static final Logger LOG =
		Logger.getLogger(SocketPlugin.class.getName());

	protected final StreamPluginCallback callback;

	protected ServerSocket socket = null; // Locking: this

	protected abstract void setLocalSocketAddress(SocketAddress s);

	// These methods must only be called with this's lock held and
	// started == true
	protected abstract Socket createClientSocket() throws IOException;
	protected abstract ServerSocket createServerSocket() throws IOException;
	protected abstract SocketAddress getLocalSocketAddress();
	protected abstract SocketAddress getRemoteSocketAddress(ContactId c);

	protected SocketPlugin(@PluginExecutor Executor pluginExecutor,
			StreamPluginCallback callback) {
		super(pluginExecutor);
		this.callback = callback;
	}

	@Override
	public synchronized void start() throws IOException {
		super.start();
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
			synchronized(this) {
				if(!running) return;
				addr = getLocalSocketAddress();
				ss = createServerSocket();
				if(addr == null || ss == null) return;
			}
			ss.bind(addr);
			if(LOG.isLoggable(Level.INFO)) {
				LOG.info("Bound to " + ss.getInetAddress().getHostAddress()
						+ ":" + ss.getLocalPort());
			}
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
			if(ss != null) {
				try {
					ss.close();
				} catch(IOException e1) {
					if(LOG.isLoggable(Level.WARNING))
						LOG.warning(e1.toString());
				}
			}
			return;
		}
		synchronized(this) {
			if(!running) {
				try {
					ss.close();
				} catch(IOException e) {
					if(LOG.isLoggable(Level.WARNING))
						LOG.warning(e.toString());
				}
				return;
			}
			socket = ss;
			setLocalSocketAddress(ss.getLocalSocketAddress());
		}
		// Accept connections until the socket is closed
		while(true) {
			Socket s;
			synchronized(this) {
				if(!running) return;
			}
			try {
				s = ss.accept();
			} catch(IOException e) {
				// This is expected when the socket is closed
				if(LOG.isLoggable(Level.INFO)) LOG.info(e.toString());
				try {
					ss.close();
				} catch(IOException e1) {
					if(LOG.isLoggable(Level.WARNING))
						LOG.warning(e1.toString());
				}
				return;
			}
			SocketTransportConnection conn = new SocketTransportConnection(s);
			callback.incomingConnectionCreated(conn);
		}
	}

	@Override
	public synchronized void stop() throws IOException {
		super.stop();
		if(socket != null) socket.close();
	}

	public void poll() {
		Map<ContactId, TransportProperties> remote;
		synchronized(this) {
			if(!running) return;
			remote = callback.getRemoteProperties();
		}
		for(final ContactId c : remote.keySet()) {
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
		SocketAddress addr;
		Socket s;
		try {
			synchronized(this) {
				if(!running) return null;
				addr = getRemoteSocketAddress(c);
				s = createClientSocket();
				if(addr == null || s == null) return null;
			}
			s.connect(addr);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.INFO)) LOG.info(e.toString());
			return null;
		}
		return new SocketTransportConnection(s);
	}
}
