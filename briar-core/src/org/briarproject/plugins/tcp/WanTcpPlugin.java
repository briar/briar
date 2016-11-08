package org.briarproject.plugins.tcp;

import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.api.plugins.Backoff;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.properties.TransportProperties;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class WanTcpPlugin extends TcpPlugin {

	static final TransportId ID = new TransportId("wan");

	private static final String PROP_IP_PORT = "ipPort";

	private final PortMapper portMapper;

	private volatile MappingResult mappingResult;

	WanTcpPlugin(Executor ioExecutor, Backoff backoff, PortMapper portMapper,
			DuplexPluginCallback callback, int maxLatency, int maxIdleTime) {
		super(ioExecutor, backoff, callback, maxLatency, maxIdleTime);
		this.portMapper = portMapper;
	}

	@Override
	public TransportId getId() {
		return ID;
	}

	@Override
	protected List<InetSocketAddress> getLocalSocketAddresses() {
		// Use the same address and port as last time if available
		TransportProperties p = callback.getLocalProperties();
		InetSocketAddress old = parseSocketAddress(p.get(PROP_IP_PORT));
		List<InetSocketAddress> addrs = new LinkedList<InetSocketAddress>();
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
	protected List<InetSocketAddress> getRemoteSocketAddresses(ContactId c) {
		TransportProperties p = callback.getRemoteProperties().get(c);
		if (p == null) return Collections.emptyList();
		InetSocketAddress parsed = parseSocketAddress(p.get(PROP_IP_PORT));
		if (parsed == null) return Collections.emptyList();
		return Collections.singletonList(parsed);
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
		p.put(PROP_IP_PORT, getIpPortString(a));
		callback.mergeLocalProperties(p);
	}
}
