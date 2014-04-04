package org.briarproject.plugins.tcp;

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
import java.util.regex.Pattern;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.crypto.PseudoRandom;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.util.StringUtils;

abstract class TcpPlugin implements DuplexPlugin {

	private static final Pattern DOTTED_QUAD =
			Pattern.compile("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");
	private static final Logger LOG =
			Logger.getLogger(TcpPlugin.class.getName());

	protected final Executor pluginExecutor;
	protected final DuplexPluginCallback callback;
	protected final int maxFrameLength;
	protected final long maxLatency, pollingInterval;

	protected volatile boolean running = false;
	protected volatile ServerSocket socket = null;

	/**
	 * Returns zero or more socket addresses on which the plugin should listen,
	 * in order of preference. At most one of the addresses will be bound.
	 */
	protected abstract List<SocketAddress> getLocalSocketAddresses();

	protected TcpPlugin(Executor pluginExecutor, DuplexPluginCallback callback,
			int maxFrameLength, long maxLatency, long pollingInterval) {
		this.pluginExecutor = pluginExecutor;
		this.callback = callback;
		this.maxFrameLength = maxFrameLength;
		this.maxLatency = maxLatency;
		this.pollingInterval = pollingInterval;
	}

	public int getMaxFrameLength() {
		return maxFrameLength;
	}

	public long getMaxLatency() {
		return maxLatency;
	}

	public boolean start() {
		running = true;
		bind();
		return true;
	}

	protected void bind() {
		pluginExecutor.execute(new Runnable() {
			public void run() {
				if(!running) return;
				ServerSocket ss = null;
				for(SocketAddress addr : getLocalSocketAddresses()) {
					try {
						ss = new ServerSocket();
						ss.bind(addr);
						break;
					} catch(IOException e) {
						if(LOG.isLoggable(INFO))
							LOG.info("Failed to bind " + addr);
						tryToClose(ss);
						continue;
					}
				}
				if(ss == null || !ss.isBound()) {
					LOG.info("Could not bind server socket");
					return;
				}
				if(!running) {
					tryToClose(ss);
					return;
				}
				socket = ss;
				SocketAddress local = ss.getLocalSocketAddress();
				setLocalSocketAddress((InetSocketAddress) local);
				if(LOG.isLoggable(INFO)) LOG.info("Listening on " + local);
				acceptContactConnections();
			}
		});
	}

	protected void tryToClose(ServerSocket ss) {
		try {
			if(ss != null) ss.close();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	protected String getHostAddress(InetAddress a) {
		String addr = a.getHostAddress();
		int percent = addr.indexOf('%');
		return percent == -1 ? addr : addr.substring(0, percent);
	}

	protected void setLocalSocketAddress(InetSocketAddress a) {
		TransportProperties p = new TransportProperties();
		p.put("address", getHostAddress(a.getAddress()));
		p.put("port", String.valueOf(a.getPort()));
		callback.mergeLocalProperties(p);
	}

	private void acceptContactConnections() {
		while(isRunning()) {
			Socket s;
			try {
				s = socket.accept();
			} catch(IOException e) {
				// This is expected when the socket is closed
				if(LOG.isLoggable(INFO)) LOG.info(e.toString());
				tryToClose(socket);
				return;
			}
			if(LOG.isLoggable(INFO))
				LOG.info("Connection from " + s.getRemoteSocketAddress());
			TcpTransportConnection conn = new TcpTransportConnection(this, s);
			callback.incomingConnectionCreated(conn);
		}
	}

	public void stop() {
		running = false;
		tryToClose(socket);
	}

	public boolean isRunning() {
		return running && socket != null && !socket.isClosed();
	}

	public boolean shouldPoll() {
		return true;
	}

	public long getPollingInterval() {
		return pollingInterval;
	}

	public void poll(Collection<ContactId> connected) {
		if(!isRunning()) return;
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
		if(!isRunning()) return null;
		SocketAddress addr = getRemoteSocketAddress(c);
		if(addr == null) return null;
		Socket s = new Socket();
		try {
			if(LOG.isLoggable(INFO)) LOG.info("Connecting to " + addr);
			s.connect(addr);
			if(LOG.isLoggable(INFO)) LOG.info("Connected to " + addr);
			return new TcpTransportConnection(this, s);
		} catch(IOException e) {
			if(LOG.isLoggable(INFO)) LOG.info("Could not connect to " + addr);
			return null;
		}
	}

	private SocketAddress getRemoteSocketAddress(ContactId c) {
		TransportProperties p = callback.getRemoteProperties().get(c);
		if(p == null) return null;
		return parseSocketAddress(p.get("address"), p.get("port"));
	}

	protected InetSocketAddress parseSocketAddress(String addr, String port) {
		if(StringUtils.isNullOrEmpty(addr)) return null;
		if(StringUtils.isNullOrEmpty(port)) return null;
		// Ensure getByName() won't perform a DNS lookup
		if(!DOTTED_QUAD.matcher(addr).matches()) return null;
		try {
			InetAddress a = InetAddress.getByName(addr);
			int p = Integer.parseInt(port);
			return new InetSocketAddress(a, p);
		} catch(UnknownHostException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning("Invalid address: " + addr);
			return null;
		} catch(NumberFormatException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning("Invalid port: " + port);
			return null;
		}
	}

	public boolean supportsInvitations() {
		return false;
	}

	public DuplexTransportConnection createInvitationConnection(PseudoRandom r,
			long timeout) {
		throw new UnsupportedOperationException();
	}
}
