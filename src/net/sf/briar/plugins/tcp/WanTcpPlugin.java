package net.sf.briar.plugins.tcp;

import static java.util.logging.Level.WARNING;

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

import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.util.StringUtils;

class WanTcpPlugin extends TcpPlugin {

	public static final byte[] TRANSPORT_ID =
			StringUtils.fromHexString("58c66d999e492b85065924acfd739d80"
					+ "c65a62f87e5a4fc6c284f95908b9007d"
					+ "512a93ebf89bf68f50a29e96eebf97b6");

	private static final TransportId ID = new TransportId(TRANSPORT_ID);
	private static final Logger LOG =
			Logger.getLogger(WanTcpPlugin.class.getName());

	private final PortMapper portMapper;

	private volatile MappingResult mappingResult;

	WanTcpPlugin(@PluginExecutor Executor pluginExecutor,
			DuplexPluginCallback callback, long pollingInterval,
			PortMapper portMapper) {
		super(pluginExecutor, callback, pollingInterval);
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
		if(addrString != null && portString != null) {
			try {
				addr = InetAddress.getByName(addrString);
				port = Integer.valueOf(portString);
				addrs.add(new InetSocketAddress(addr, port));
				addrs.add(new InetSocketAddress(addr, 0));
			} catch(NumberFormatException e) {
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			} catch(UnknownHostException e) {
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			}
		}
		// Get a list of the device's network interfaces
		List<NetworkInterface> ifaces;
		try {
			ifaces = Collections.list(NetworkInterface.getNetworkInterfaces());
		} catch(SocketException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			return addrs;
		}
		// Accept interfaces without link-local or site-local addresses
		for(NetworkInterface iface : ifaces) {
			for(InetAddress a : Collections.list(iface.getInetAddresses())) {
				if(addr != null && a.equals(addr)) continue;
				if(a.isLoopbackAddress()) continue;
				boolean link = a.isLinkLocalAddress();
				boolean site = a.isSiteLocalAddress();
				if(!link && !site) addrs.add(new InetSocketAddress(a, 0));
			}
		}
		// Accept interfaces with local addresses that can be port-mapped
		if(port == 0) port = chooseEphemeralPort();
		mappingResult = portMapper.map(port);
		if(mappingResult != null && mappingResult.isUsable())
			addrs.add(mappingResult.getInternal());
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
		p.put("address", a.getAddress().getHostAddress());
		p.put("port", String.valueOf(a.getPort()));
		callback.mergeLocalProperties(p);
	}

	public boolean supportsInvitations() {
		return false;
	}

	public DuplexTransportConnection sendInvitation(PseudoRandom r,
			long timeout) {
		throw new UnsupportedOperationException();
	}

	public DuplexTransportConnection acceptInvitation(PseudoRandom r,
			long timeout) {
		throw new UnsupportedOperationException();
	}
}
