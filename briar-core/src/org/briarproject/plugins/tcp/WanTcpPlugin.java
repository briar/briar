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
		// Get a list of the device's network interfaces
		List<NetworkInterface> ifaces;
		try {
			ifaces = Collections.list(NetworkInterface.getNetworkInterfaces());
		} catch(SocketException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return Collections.emptyList();
		}
		List<SocketAddress> addresses = new LinkedList<SocketAddress>();
		// Accept interfaces without link-local or site-local addresses
		for(NetworkInterface iface : ifaces) {
			for(InetAddress a : Collections.list(iface.getInetAddresses())) {
				boolean ipv4 = a instanceof Inet4Address;
				boolean loop = a.isLoopbackAddress();
				boolean link = a.isLinkLocalAddress();
				boolean site = a.isSiteLocalAddress();
				if(ipv4 && !loop && !link && !site) {
					if(a.equals(oldAddress))
						addresses.add(0, new InetSocketAddress(a, oldPort));
					addresses.add(new InetSocketAddress(a, 0));
				}
			}
		}
		// Accept interfaces with local addresses that can be port-mapped
		if(oldPort == 0) oldPort = chooseEphemeralPort();
		mappingResult = portMapper.map(oldPort);
		if(mappingResult != null && mappingResult.isUsable()) {
			InetSocketAddress a = mappingResult.getInternal();
			if(a.getAddress() instanceof Inet4Address) addresses.add(a);
		}
		return addresses;
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
}
