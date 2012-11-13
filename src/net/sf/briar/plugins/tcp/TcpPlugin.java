package net.sf.briar.plugins.tcp;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;

abstract class TcpPlugin implements DuplexPlugin {

	private static final Logger LOG =
			Logger.getLogger(TcpPlugin.class.getName());

	protected final Executor pluginExecutor;
	protected final DuplexPluginCallback callback;
	protected final long pollingInterval;

	protected boolean running = false; // Locking: this
	private ServerSocket socket = null; // Locking: this

	/**
	 * Returns zero or more socket addresses on which the plugin should listen,
	 * in order of preference. At most one of the addresses will be bound.
	 */
	protected abstract List<SocketAddress> getLocalSocketAddresses();

	protected TcpPlugin(@PluginExecutor Executor pluginExecutor,
			DuplexPluginCallback callback, long pollingInterval) {
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
		ServerSocket ss;
		try {
			ss = new ServerSocket();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			return;
		}
		boolean found = false;
		for(SocketAddress addr : getLocalSocketAddresses()) {
			try {
				ss.bind(addr);
				found = true;
				break;
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
				tryToClose(ss);
				continue;
			}
		}
		if(!found) {
			if(LOG.isLoggable(INFO)) LOG.info("Could not bind server socket");
			return;
		}
		synchronized(this) {
			if(!running) {
				tryToClose(ss);
				return;
			}
			socket = ss;
		}
		if(LOG.isLoggable(INFO)) {
			String addr = ss.getInetAddress().getHostAddress();
			int port = ss.getLocalPort();
			LOG.info("Listening on " + addr + " " + port);
		}
		setLocalSocketAddress((InetSocketAddress) ss.getLocalSocketAddress());
		acceptContactConnections(ss);
	}

	private void tryToClose(ServerSocket ss) {
		try {
			ss.close();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		}
	}

	protected void setLocalSocketAddress(InetSocketAddress a) {
		InetAddress addr = a.getAddress();
		TransportProperties p = new TransportProperties();
		p.put("address", addr.getHostAddress());
		p.put("port", String.valueOf(a.getPort()));
		callback.mergeLocalProperties(p);
	}

	private void acceptContactConnections(ServerSocket ss) {
		while(true) {
			Socket s;
			try {
				s = ss.accept();
			} catch(IOException e) {
				// This is expected when the socket is closed
				if(LOG.isLoggable(INFO)) LOG.info(e.toString());
				tryToClose(ss);
				return;
			}
			TcpTransportConnection conn = new TcpTransportConnection(s);
			callback.incomingConnectionCreated(conn);
			synchronized(this) {
				if(!running) return;
			}
		}
	}

	public synchronized void stop() throws IOException {
		running = false;
		if(socket != null) {
			tryToClose(socket);
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
		DuplexTransportConnection d = createConnection(c);
		if(d != null) callback.outgoingConnectionCreated(c, d);
	}

	public DuplexTransportConnection createConnection(ContactId c) {
		synchronized(this) {
			if(!running) return null;
		}
		SocketAddress addr = getRemoteSocketAddress(c);
		Socket s = new Socket();
		if(addr == null || s == null) return null;
		try {
			s.connect(addr);
			return new TcpTransportConnection(s);
		} catch(IOException e) {
			if(LOG.isLoggable(INFO)) LOG.info(e.toString());
			return null;
		}
	}

	private SocketAddress getRemoteSocketAddress(ContactId c) {
		TransportProperties p = callback.getRemoteProperties().get(c);
		if(p == null) return null;
		String addrString = p.get("address");
		String portString = p.get("port");
		if(addrString != null && portString != null) {
			try {
				InetAddress addr = InetAddress.getByName(addrString);
				int port = Integer.valueOf(portString);
				return new InetSocketAddress(addr, port);
			} catch(NumberFormatException e) {
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			} catch(UnknownHostException e) {
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			}
		}
		return null;
	}
}
