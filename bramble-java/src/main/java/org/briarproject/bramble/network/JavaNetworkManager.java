package org.briarproject.bramble.network;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.Service;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.network.NetworkStatus;
import org.briarproject.bramble.api.network.event.NetworkStatusEvent;
import org.briarproject.bramble.api.system.TaskScheduler;
import org.briarproject.nullsafety.NotNullByDefault;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.Collections.list;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.NetworkUtils.getNetworkInterfaces;

@NotNullByDefault
class JavaNetworkManager implements NetworkManager, Service {

	private static final Logger LOG =
			getLogger(JavaNetworkManager.class.getName());

	private final TaskScheduler scheduler;
	private final Executor ioExecutor;
	private final EventBus eventBus;
	private final AtomicReference<NetworkStatus> lastStatus =
			new AtomicReference<>();

	@Inject
	JavaNetworkManager(TaskScheduler scheduler,
			@IoExecutor Executor ioExecutor,
			EventBus eventBus) {
		this.scheduler = scheduler;
		this.ioExecutor = ioExecutor;
		this.eventBus = eventBus;
	}

	@Override
	public NetworkStatus getNetworkStatus() {
		boolean connected = false, hasIpv4 = false, hasIpv6Unicast = false;
		try {
			for (NetworkInterface i : getNetworkInterfaces()) {
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
		} catch (SocketException e) {
			logException(LOG, WARNING, e);
		}
		if (LOG.isLoggable(INFO)) {
			LOG.info("Connected: " + connected
					+ ", has IPv4 address: " + hasIpv4
					+ ", has IPv6 unicast address: " + hasIpv6Unicast);
		}
		return new NetworkStatus(connected, false, !hasIpv4 && hasIpv6Unicast);
	}

	private void broadcastNetworkStatusIfChanged() {
		NetworkStatus status = getNetworkStatus();
		NetworkStatus old = lastStatus.getAndSet(status);
		if (!status.equals(old)) {
			eventBus.broadcast(new NetworkStatusEvent(status));
		}
	}

	@Override
	public void startService() {
		scheduler.scheduleWithFixedDelay(this::broadcastNetworkStatusIfChanged,
				ioExecutor, 0, 10, SECONDS);
	}

	@Override
	public void stopService() {
	}
}
