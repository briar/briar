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
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.util.StringUtils;

abstract class TcpPlugin implements DuplexPlugin {

	private static final Logger LOG =
			Logger.getLogger(TcpPlugin.class.getName());

	protected final Executor pluginExecutor;
	protected final DuplexPluginCallback callback;
	protected final long maxLatency, pollingInterval;

	protected volatile boolean running = false;
	private volatile ServerSocket socket = null;

	/**
	 * Returns zero or more socket addresses on which the plugin should listen,
	 * in order of preference. At most one of the addresses will be bound.
	 */
	protected abstract List<SocketAddress> getLocalSocketAddresses();

	protected TcpPlugin(Executor pluginExecutor, DuplexPluginCallback callback,
			long maxLatency, long pollingInterval) {
		this.pluginExecutor = pluginExecutor;
		this.callback = callback;
		this.maxLatency = maxLatency;
		this.pollingInterval = pollingInterval;
	}

	public long getMaxLatency() {
		return maxLatency;
	}

	public boolean start() {
		running = true;
		pluginExecutor.execute(new Runnable() {
			public void run() {
				bind();
			}
		});
		return true;
	}

	private void bind() {
		ServerSocket ss;
		try {
			ss = new ServerSocket();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return;
		}
		boolean found = false;
		for(SocketAddress addr : getLocalSocketAddresses()) {
			try {
				ss.bind(addr);
				found = true;
				break;
			} catch(IOException e) {
				if(LOG.isLoggable(INFO)) LOG.info("Failed to bind " + addr);
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				tryToClose(ss);
				continue;
			}
		}
		if(!found) {
			if(LOG.isLoggable(INFO)) LOG.info("Could not bind server socket");
			return;
		}
		if(!running) {
			tryToClose(ss);
			return;
		}
		socket = ss;
		if(LOG.isLoggable(INFO)) {
			String addr = getHostAddress(ss.getInetAddress());
			int port = ss.getLocalPort();
			LOG.info("Listening on " + addr + " " + port);
		}
		setLocalSocketAddress((InetSocketAddress) ss.getLocalSocketAddress());
		acceptContactConnections(ss);
	}

	protected void tryToClose(ServerSocket ss) {
		try {
			ss.close();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	protected String getHostAddress(InetAddress a) {
		String addr = a.getHostAddress();
		int percent = addr.indexOf('%');
		if(percent == -1) return addr;
		return addr.substring(0, percent);
	}

	protected void setLocalSocketAddress(InetSocketAddress a) {
		TransportProperties p = new TransportProperties();
		p.put("address", getHostAddress(a.getAddress()));
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
				if(LOG.isLoggable(INFO)) LOG.log(INFO, e.toString(), e);
				tryToClose(ss);
				return;
			}
			callback.incomingConnectionCreated(new TcpTransportConnection(s,
					maxLatency));
			if(!running) return;
		}
	}

	public void stop() {
		running = false;
		if(socket != null) tryToClose(socket);
	}

	public boolean shouldPoll() {
		return true;
	}

	public long getPollingInterval() {
		return pollingInterval;
	}

	public void poll(Collection<ContactId> connected) {
		if(!running) return;
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
		if(!running) return null;
		SocketAddress addr = getRemoteSocketAddress(c);
		if(addr == null) return null;
		Socket s = new Socket();
		try {
			s.connect(addr);
			return new TcpTransportConnection(s, maxLatency);
		} catch(IOException e) {
			if(LOG.isLoggable(INFO)) LOG.log(INFO, e.toString(), e);
			return null;
		}
	}

	private SocketAddress getRemoteSocketAddress(ContactId c) {
		TransportProperties p = callback.getRemoteProperties().get(c);
		if(p == null) return null;
		String addrString = p.get("address");
		if(StringUtils.isNullOrEmpty(addrString)) return null;
		String portString = p.get("port");
		if(StringUtils.isNullOrEmpty(portString)) return null;
		try {
			InetAddress addr = InetAddress.getByName(addrString);
			int port = Integer.valueOf(portString);
			return new InetSocketAddress(addr, port);
		} catch(NumberFormatException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		} catch(UnknownHostException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		}
	}
}
