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
import org.briarproject.api.crypto.PseudoRandom;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.util.StringUtils;

class WanTcpPlugin extends TcpPlugin {

	static final TransportId ID = new TransportId("wan");

	private static final Logger LOG =
			Logger.getLogger(WanTcpPlugin.class.getName());

	private final PortMapper portMapper;

	private volatile MappingResult mappingResult;

	WanTcpPlugin(Executor pluginExecutor, DuplexPluginCallback callback,
			int maxFrameLength, long maxLatency, long pollingInterval,
			PortMapper portMapper) {
		super(pluginExecutor, callback, maxFrameLength, maxLatency,
				pollingInterval);
		this.portMapper = portMapper;
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
		int port = 0;
		if(!StringUtils.isNullOrEmpty(addrString) &&
				!StringUtils.isNullOrEmpty(portString)) {
			try {
				addr = InetAddress.getByName(addrString);
				port = Integer.parseInt(portString);
				addrs.add(new InetSocketAddress(addr, port));
				addrs.add(new InetSocketAddress(addr, 0));
			} catch(NumberFormatException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			} catch(UnknownHostException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
		// Get a list of the device's network interfaces
		List<NetworkInterface> ifaces;
		try {
			ifaces = Collections.list(NetworkInterface.getNetworkInterfaces());
		} catch(SocketException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return addrs;
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
		// Accept interfaces with local addresses that can be port-mapped
		if(port == 0) port = chooseEphemeralPort();
		mappingResult = portMapper.map(port);
		if(mappingResult != null && mappingResult.isUsable()) {
			InetSocketAddress a = mappingResult.getInternal();
			if(!(a.getAddress() instanceof Inet6Address)) addrs.add(a);
		}
		return addrs;
	}

	private int chooseEphemeralPort() {
		return 32768 + (int) (Math.random() * 32768);
	}

	@Override
	protected void setLocalSocketAddress(InetSocketAddress a) {
		if(mappingResult != null && mappingResult.isUsable()) {
			// Advertise the external address to contacts
			if(a.equals(mappingResult.getInternal()))
				a = mappingResult.getExternal();
		}
		TransportProperties p = new TransportProperties();
		p.put("address", getHostAddress(a.getAddress()));
		p.put("port", String.valueOf(a.getPort()));
		callback.mergeLocalProperties(p);
	}

	public boolean supportsInvitations() {
		return false;
	}

	public DuplexTransportConnection createInvitationConnection(PseudoRandom r,
			long timeout) {
		throw new UnsupportedOperationException();
	}
}
