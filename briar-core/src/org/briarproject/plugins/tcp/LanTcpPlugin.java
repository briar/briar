package org.briarproject.plugins.tcp;

import static java.util.logging.Level.WARNING;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.util.StringUtils;

/** A TCP plugin that supports exchanging invitations over a LAN. */
class LanTcpPlugin extends TcpPlugin {

	static final TransportId ID = new TransportId("lan");

	private static final Logger LOG =
			Logger.getLogger(LanTcpPlugin.class.getName());

	LanTcpPlugin(Executor pluginExecutor, DuplexPluginCallback callback,
			int maxFrameLength, long maxLatency, long pollingInterval) {
		super(pluginExecutor, callback, maxFrameLength, maxLatency,
				pollingInterval);
	}

	public TransportId getId() {
		return ID;
	}

	@Override
	protected List<SocketAddress> getLocalSocketAddresses() {
		// Use the same address and port as last time if available
		TransportProperties p = callback.getLocalProperties();
		String addressString = p.get("address");
		String portString = p.get("port");
		InetAddress oldAddress = null;
		int oldPort = 0;
		if(!StringUtils.isNullOrEmpty(addressString) &&
				!StringUtils.isNullOrEmpty(portString)) {
			try {
				oldAddress = InetAddress.getByName(addressString);
				oldPort = Integer.parseInt(portString);
			} catch(NumberFormatException e) {
				if(LOG.isLoggable(WARNING))
					LOG.warning("Invalid port: " + portString);
			} catch(UnknownHostException e) {
				if(LOG.isLoggable(WARNING))
					LOG.warning("Invalid address: " + addressString);
			}
		}
		List<NetworkInterface> ifaces;
		try {
			ifaces = Collections.list(NetworkInterface.getNetworkInterfaces());
		} catch(SocketException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return Collections.emptyList();
		}
		List<SocketAddress> addresses = new LinkedList<SocketAddress>();
		// Accept interfaces with link-local or site-local addresses
		for(NetworkInterface iface : ifaces) {
			for(InetAddress a : Collections.list(iface.getInetAddresses())) {
				boolean ipv4 = a instanceof Inet4Address;
				boolean loop = a.isLoopbackAddress();
				boolean link = a.isLinkLocalAddress();
				boolean site = a.isSiteLocalAddress();
				if(ipv4 && !loop && (link || site)) {
					if(a.equals(oldAddress))
						addresses.add(0, new InetSocketAddress(a, oldPort));
					addresses.add(new InetSocketAddress(a, 0));
				}
			}
		}
		return addresses;
	}
}