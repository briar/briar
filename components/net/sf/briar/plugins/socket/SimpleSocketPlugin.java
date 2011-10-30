package net.sf.briar.plugins.socket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.plugins.StreamPluginCallback;
import net.sf.briar.api.transport.StreamTransportConnection;

class SimpleSocketPlugin extends SocketPlugin {

	public static final int TRANSPORT_ID = 1;

	private static final TransportId id = new TransportId(TRANSPORT_ID);
	private static final Logger LOG =
		Logger.getLogger(SimpleSocketPlugin.class.getName());

	private final long pollingInterval;

	SimpleSocketPlugin(Executor executor, StreamPluginCallback callback,
			long pollingInterval) {
		super(executor, callback);
		this.pollingInterval = pollingInterval;
	}

	public TransportId getId() {
		return id;
	}

	public boolean shouldPoll() {
		return true;
	}

	public long getPollingInterval() {
		return pollingInterval;
	}

	@Override
	protected Socket createClientSocket() throws IOException {
		assert started;
		return new Socket();
	}

	@Override
	protected ServerSocket createServerSocket() throws IOException {
		assert started;
		return new ServerSocket();
	}

	@Override
	protected synchronized SocketAddress getLocalSocketAddress() {
		assert started;
		SocketAddress addr = createSocketAddress(callback.getLocalProperties());
		if(addr == null) {
			try {
				return new InetSocketAddress(chooseTcpInterface(false), 0);
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
		return addr;
	}

	protected InetAddress chooseTcpInterface(boolean lan) throws IOException {
		List<NetworkInterface> ifaces =
			Collections.list(NetworkInterface.getNetworkInterfaces());
		// Try to find an interface of the preferred type (LAN or WAN)
		for(NetworkInterface iface : ifaces) {
			for(InetAddress addr : Collections.list(iface.getInetAddresses())) {
				if(!addr.isLoopbackAddress()) {
					boolean link = addr.isLinkLocalAddress();
					boolean site = addr.isSiteLocalAddress();
					if(lan == (link || site)) {
						if(LOG.isLoggable(Level.INFO))
							LOG.info("Preferring " + addr.getHostAddress());
						return addr;
					}
				}
			}
		}
		// Settle for an interface that's not of the preferred type
		for(NetworkInterface iface : ifaces) {
			for(InetAddress addr : Collections.list(iface.getInetAddresses())) {
				if(!addr.isLoopbackAddress()) {
					if(LOG.isLoggable(Level.INFO))
						LOG.info("Accepting " + addr.getHostAddress());
					return addr;
				}
			}
		}
		throw new IOException("No suitable interfaces for TCP");
	}

	@Override
	protected synchronized SocketAddress getRemoteSocketAddress(ContactId c) {
		assert started;
		TransportProperties p = callback.getRemoteProperties().get(c);
		return p == null ? null : createSocketAddress(p);
	}

	private synchronized SocketAddress createSocketAddress(
			TransportProperties p) {
		assert started;
		assert p != null;
		String host = p.get("external");
		if(host == null) host = p.get("internal");
		String portString = p.get("port");
		if(host == null || portString == null) return null;
		int port;
		try {
			port = Integer.valueOf(portString);
		} catch(NumberFormatException e) {
			return null;
		}
		return new InetSocketAddress(host, port);
	}

	@Override
	protected synchronized void setLocalSocketAddress(SocketAddress s) {
		assert started;
		if(!(s instanceof InetSocketAddress))
			throw new IllegalArgumentException();
		InetSocketAddress i = (InetSocketAddress) s;
		InetAddress addr = i.getAddress();
		TransportProperties p = callback.getLocalProperties();
		if(addr.isLinkLocalAddress() || addr.isSiteLocalAddress())
			p.put("internal", addr.getHostAddress());
		else p.put("external", addr.getHostAddress());
		p.put("port", String.valueOf(i.getPort()));
		callback.setLocalProperties(p);
	}

	public boolean supportsInvitations() {
		return false;
	}

	public StreamTransportConnection sendInvitation(int code, long timeout) {
		throw new UnsupportedOperationException();
	}

	public StreamTransportConnection acceptInvitation(int code, long timeout) {
		throw new UnsupportedOperationException();
	}
}
