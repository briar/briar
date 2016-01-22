package org.briarproject.plugins.tcp;

import org.briarproject.api.TransportId;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.properties.TransportProperties;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;

class WanTcpPlugin extends TcpPlugin {

	static final TransportId ID = new TransportId("wan");

	private final PortMapper portMapper;

	private volatile MappingResult mappingResult;

	WanTcpPlugin(Executor ioExecutor, PortMapper portMapper,
			DuplexPluginCallback callback, int maxLatency, int maxIdleTime,
			int pollingInterval) {
		super(ioExecutor, callback, maxLatency, maxIdleTime, pollingInterval);
		this.portMapper = portMapper;
	}

	public TransportId getId() {
		return ID;
	}

	@Override
	protected List<SocketAddress> getLocalSocketAddresses() {
		// Use the same address and port as last time if available
		TransportProperties p = callback.getLocalProperties();
		String oldAddress = p.get("address"), oldPort = p.get("port");
		InetSocketAddress old = parseSocketAddress(oldAddress, oldPort);
		List<SocketAddress> addrs = new LinkedList<SocketAddress>();
		for (InetAddress a : getLocalIpAddresses()) {
			if (isAcceptableAddress(a)) {
				// If this is the old address, try to use the same port
				if (old != null && old.getAddress().equals(a))
					addrs.add(0, new InetSocketAddress(a, old.getPort()));
				addrs.add(new InetSocketAddress(a, 0));
			}
		}
		// Accept interfaces with local addresses that can be port-mapped
		int port = old == null ? chooseEphemeralPort() : old.getPort();
		mappingResult = portMapper.map(port);
		if (mappingResult != null && mappingResult.isUsable()) {
			InetSocketAddress a = mappingResult.getInternal();
			if (a.getAddress() instanceof Inet4Address) addrs.add(a);
		}
		return addrs;
	}

	private boolean isAcceptableAddress(InetAddress a) {
		// Accept global IPv4 addresses
		boolean ipv4 = a instanceof Inet4Address;
		boolean loop = a.isLoopbackAddress();
		boolean link = a.isLinkLocalAddress();
		boolean site = a.isSiteLocalAddress();
		return ipv4 && !loop && !link && !site;
	}

	private int chooseEphemeralPort() {
		return 32768 + (int) (Math.random() * 32768);
	}

	@Override
	protected boolean isConnectable(InetSocketAddress remote) {
		if (remote.getPort() == 0) return false;
		return isAcceptableAddress(remote.getAddress());
	}

	@Override
	protected void setLocalSocketAddress(InetSocketAddress a) {
		if (mappingResult != null && mappingResult.isUsable()) {
			// Advertise the external address to contacts
			if (a.equals(mappingResult.getInternal()))
				a = mappingResult.getExternal();
		}
		TransportProperties p = new TransportProperties();
		p.put("address", getHostAddress(a.getAddress()));
		p.put("port", String.valueOf(a.getPort()));
		callback.mergeLocalProperties(p);
	}
}
