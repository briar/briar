package org.briarproject.bramble.network;

import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.network.NetworkStatus;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.net.NetworkInterface.getNetworkInterfaces;
import static java.util.Collections.list;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class JavaNetworkManager implements NetworkManager {

	private static final Logger LOG =
			getLogger(JavaNetworkManager.class.getName());

	@Inject
	JavaNetworkManager() {
	}

	@Override
	public NetworkStatus getNetworkStatus() {
		boolean connected = false, hasIpv4 = false, hasIpv6Unicast = false;
		try {
			Enumeration<NetworkInterface> interfaces = getNetworkInterfaces();
			if (interfaces == null) {
				LOG.info("No network interfaces");
			} else {
				for (NetworkInterface i : list(interfaces)) {
					if (i.isLoopback() || !i.isUp()) continue;
					for (InetAddress addr : list(i.getInetAddresses())) {
						connected = true;
						if (addr instanceof Inet4Address) {
							hasIpv4 = true;
						} else if (!addr.isMulticastAddress()) {
							hasIpv6Unicast = true;
						}
					}
				}
			}
		} catch (SocketException e) {
			logException(LOG, WARNING, e);
		}
		return new NetworkStatus(connected, false, !hasIpv4 && hasIpv6Unicast);
	}

}
