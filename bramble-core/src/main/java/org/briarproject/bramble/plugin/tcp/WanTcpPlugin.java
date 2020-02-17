package org.briarproject.bramble.plugin.tcp;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.briarproject.bramble.api.plugin.WanTcpConstants.ID;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class WanTcpPlugin extends TcpPlugin {

	private static final String PROP_IP_PORT = "ipPort";

	private final PortMapper portMapper;

	private volatile MappingResult mappingResult;

	WanTcpPlugin(Executor ioExecutor, Backoff backoff, PortMapper portMapper,
			PluginCallback callback, int maxLatency, int maxIdleTime,
			int connectionTimeout) {
		super(ioExecutor, backoff, callback, maxLatency, maxIdleTime,
				connectionTimeout);
		this.portMapper = portMapper;
	}

	@Override
	public TransportId getId() {
		return ID;
	}

	@Override
	protected List<InetSocketAddress> getLocalSocketAddresses(boolean ipv4) {
		if (!ipv4) return emptyList();
		// Use the same address and port as last time if available
		TransportProperties p = callback.getLocalProperties();
		InetSocketAddress old = parseIpv4SocketAddress(p.get(PROP_IP_PORT));
		List<InetSocketAddress> addrs = new LinkedList<>();
		for (InetAddress a : getLocalInetAddresses()) {
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
			if (a != null && a.getAddress() instanceof Inet4Address)
				addrs.add(a);
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

	@Override
	protected List<InetSocketAddress> getRemoteSocketAddresses(
			TransportProperties p, boolean ipv4) {
		if (!ipv4) return emptyList();
		InetSocketAddress parsed = parseIpv4SocketAddress(p.get(PROP_IP_PORT));
		if (parsed == null) return emptyList();
		return singletonList(parsed);
	}

	@Override
	protected boolean isConnectable(InterfaceAddress local,
			InetSocketAddress remote) {
		if (remote.getPort() == 0) return false;
		return isAcceptableAddress(remote.getAddress());
	}

	@Override
	protected void setLocalSocketAddress(InetSocketAddress a, boolean ipv4) {
		if (!ipv4) throw new AssertionError();
		if (mappingResult != null && mappingResult.isUsable()) {
			// Advertise the external address to contacts
			if (a.equals(mappingResult.getInternal())) {
				InetSocketAddress external = mappingResult.getExternal();
				if (external != null) a = external;
			}
		}
		TransportProperties p = new TransportProperties();
		p.put(PROP_IP_PORT, getIpPortString(a));
		callback.mergeLocalProperties(p);
	}
}
