package org.briarproject.bramble.network;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.Service;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.network.NetworkStatus;
import org.briarproject.bramble.api.network.event.NetworkStatusEvent;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.net.NetworkInterface.getNetworkInterfaces;
import static java.util.Collections.list;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class JavaNetworkManager implements NetworkManager, Service {

	private static final Logger LOG =
			Logger.getLogger(JavaNetworkManager.class.getName());

	private final EventBus eventBus;

	@Inject
	JavaNetworkManager(EventBus eventBus) {
		this.eventBus = eventBus;
	}

	@Override
	public void startService() {
		eventBus.broadcast(new NetworkStatusEvent(getNetworkStatus()));
	}

	@Override
	public void stopService() {
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
