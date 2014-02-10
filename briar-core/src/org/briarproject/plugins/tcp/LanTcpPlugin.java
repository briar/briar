package org.briarproject.plugins.tcp;

import static java.util.logging.Level.WARNING;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.util.StringUtils;

/** A socket plugin that supports exchanging invitations over a LAN. */
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
		List<SocketAddress> addrs = new ArrayList<SocketAddress>();
		// Prefer a previously used address and port if available
		TransportProperties p = callback.getLocalProperties();
		String addrString = p.get("address");
		String portString = p.get("port");
		InetAddress addr = null;
		if(!StringUtils.isNullOrEmpty(addrString) &&
				!StringUtils.isNullOrEmpty(portString)) {
			try {
				addr = InetAddress.getByName(addrString);
				int port = Integer.parseInt(portString);
				addrs.add(new InetSocketAddress(addr, port));
				addrs.add(new InetSocketAddress(addr, 0));
			} catch(NumberFormatException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			} catch(UnknownHostException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
		List<NetworkInterface> ifaces;
		try {
			ifaces = Collections.list(NetworkInterface.getNetworkInterfaces());
		} catch(SocketException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return addrs;
		}
		// Prefer interfaces with link-local or site-local addresses
		for(NetworkInterface iface : ifaces) {
			for(InetAddress a : Collections.list(iface.getInetAddresses())) {
				if(addr != null && a.equals(addr)) continue;
				if(a instanceof Inet6Address) continue;
				if(a.isLoopbackAddress()) continue;
				boolean link = a.isLinkLocalAddress();
				boolean site = a.isSiteLocalAddress();
				if(link || site) addrs.add(new InetSocketAddress(a, 0));
			}
		}
		// Accept interfaces without link-local or site-local addresses
		for(NetworkInterface iface : ifaces) {
			for(InetAddress a : Collections.list(iface.getInetAddresses())) {
				if(addr != null && a.equals(addr)) continue;
				if(a instanceof Inet6Address) continue;
				if(a.isLoopbackAddress()) continue;
				boolean link = a.isLinkLocalAddress();
				boolean site = a.isSiteLocalAddress();
				if(!link && !site) addrs.add(new InetSocketAddress(a, 0));
			}
		}
		return addrs;
	}
}