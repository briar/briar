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
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.StreamPluginCallback;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.transport.StreamTransportConnection;
import net.sf.briar.util.StringUtils;

class SimpleSocketPlugin extends SocketPlugin {

	public static final byte[] TRANSPORT_ID =
		StringUtils.fromHexString("58c66d999e492b85065924acfd739d80"
				+ "c65a62f87e5a4fc6c284f95908b9007d");

	private static final TransportId id = new TransportId(TRANSPORT_ID);
	private static final Logger LOG =
		Logger.getLogger(SimpleSocketPlugin.class.getName());

	private final long pollingInterval;

	SimpleSocketPlugin(@PluginExecutor Executor pluginExecutor,
			StreamPluginCallback callback, long pollingInterval) {
		super(pluginExecutor, callback);
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
		assert running;
		return new Socket();
	}

	@Override
	protected ServerSocket createServerSocket() throws IOException {
		assert running;
		return new ServerSocket();
	}

	// Locking: this
	@Override
	protected SocketAddress getLocalSocketAddress() {
		assert running;
		SocketAddress addr = createSocketAddress(callback.getLocalProperties());
		if(addr == null) {
			try {
				return new InetSocketAddress(chooseInterface(false), 0);
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
			}
		}
		return addr;
	}

	protected InetAddress chooseInterface(boolean lan) throws IOException {
		List<NetworkInterface> ifaces =
			Collections.list(NetworkInterface.getNetworkInterfaces());
		// Try to find an interface of the preferred type (LAN or WAN)
		for(NetworkInterface iface : ifaces) {
			for(InetAddress addr : Collections.list(iface.getInetAddresses())) {
				if(!addr.isLoopbackAddress()) {
					boolean link = addr.isLinkLocalAddress();
					boolean site = addr.isSiteLocalAddress();
					if(lan == (link || site)) {
						if(LOG.isLoggable(Level.INFO)) {
							LOG.info("Choosing interface "
									+ addr.getHostAddress());
						}
						return addr;
					}
				}
			}
		}
		// Settle for an interface that's not of the preferred type
		for(NetworkInterface iface : ifaces) {
			for(InetAddress addr : Collections.list(iface.getInetAddresses())) {
				if(!addr.isLoopbackAddress()) {
					if(LOG.isLoggable(Level.INFO)) {
						LOG.info("Accepting interface "
								+ addr.getHostAddress());
					}
					return addr;
				}
			}
		}
		throw new IOException("No suitable interfaces");
	}

	// Locking: this
	@Override
	protected SocketAddress getRemoteSocketAddress(ContactId c) {
		assert running;
		TransportProperties p = callback.getRemoteProperties().get(c);
		return p == null ? null : createSocketAddress(p);
	}

	// Locking: this
	private SocketAddress createSocketAddress(TransportProperties p) {
		assert running;
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

	// Locking: this
	@Override
	protected void setLocalSocketAddress(SocketAddress s) {
		assert running;
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
