package org.briarproject.bramble.network;

import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.network.NetworkStatus;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.net.NetworkInterface.getNetworkInterfaces;
import static java.util.Collections.list;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
class JavaNetworkManager implements NetworkManager {

	private static final Logger LOG =
			getLogger(JavaNetworkManager.class.getName());

	@Inject
	JavaNetworkManager() {
	}

	@Override
	public NetworkStatus getNetworkStatus() {
		boolean connected = false;
		try {
			Enumeration<NetworkInterface> interfaces = getNetworkInterfaces();
			if (interfaces != null) {
				for (NetworkInterface i : list(interfaces)) {
					if (i.isLoopback()) continue;
					if (i.isUp() && i.getInetAddresses().hasMoreElements()) {
						if (LOG.isLoggable(INFO)) {
							LOG.info("Interface " + i.getDisplayName() +
									" is up with at least one address.");
						}
						connected = true;
						break;
					}
				}
			}
		} catch (SocketException e) {
			logException(LOG, WARNING, e);
		}
		return new NetworkStatus(connected, false);
	}

}
