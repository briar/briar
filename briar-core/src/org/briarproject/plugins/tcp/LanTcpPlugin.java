package org.briarproject.plugins.tcp;

import static java.util.logging.Level.WARNING;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;

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
		InetSocketAddress old = parseSocketAddress(p.get("address"),
				p.get("port"));
		// Get a list of the device's network interfaces
		List<NetworkInterface> ifaces;
		try {
			ifaces = Collections.list(NetworkInterface.getNetworkInterfaces());
		} catch(SocketException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return Collections.emptyList();
		}
		List<SocketAddress> addrs = new LinkedList<SocketAddress>();
		// Accept interfaces with local IPv4 addresses
		for(NetworkInterface iface : ifaces) {
			for(InetAddress a : Collections.list(iface.getInetAddresses())) {
				boolean ipv4 = a instanceof Inet4Address;
				boolean loop = a.isLoopbackAddress();
				boolean link = a.isLinkLocalAddress();
				boolean site = a.isSiteLocalAddress();
				if(ipv4 && !loop && (link || site)) {
					// If this is the old address, try to use the same port
					if(old != null && old.getAddress().equals(a))
						addrs.add(0, new InetSocketAddress(a, old.getPort()));
					addrs.add(new InetSocketAddress(a, 0));
				}
			}
		}
		return addrs;
	}
}