package org.briarproject.bramble.plugin.tcp;

import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;
import org.briarproject.bramble.api.lifecycle.ShutdownManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.annotation.concurrent.ThreadSafe;
import javax.xml.parsers.ParserConfigurationException;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.PrivacyUtils.scrubInetAddress;

@ThreadSafe
@MethodsNotNullByDefault
@ParametersNotNullByDefault
class PortMapperImpl implements PortMapper {

	private static final Logger LOG =
			Logger.getLogger(PortMapperImpl.class.getName());

	private final ShutdownManager shutdownManager;
	private final AtomicBoolean started = new AtomicBoolean(false);

	private volatile GatewayDevice gateway = null;

	PortMapperImpl(ShutdownManager shutdownManager) {
		this.shutdownManager = shutdownManager;
	}

	@Override
	public MappingResult map(int port) {
		if (!started.getAndSet(true)) start();
		if (gateway == null) return null;
		InetAddress internal = gateway.getLocalAddress();
		if (internal == null) return null;
		if (LOG.isLoggable(INFO))
			LOG.info("Internal address " + scrubInetAddress(internal));
		boolean succeeded = false;
		InetAddress external = null;
		try {
			succeeded = gateway.addPortMapping(port, port,
					getHostAddress(internal), "TCP", "TCP");
			if (succeeded) {
				shutdownManager.addShutdownHook(() -> deleteMapping(port));
			}
			String externalString = gateway.getExternalIPAddress();
			if (externalString == null) {
				LOG.info("External address not available");
			} else {
				external = InetAddress.getByName(externalString);
				if (LOG.isLoggable(INFO))
					LOG.info("External address " + scrubInetAddress(external));
			}
		} catch (IOException | SAXException e) {
			logException(LOG, WARNING, e);
		}
		return new MappingResult(internal, external, port, succeeded);
	}

	private String getHostAddress(InetAddress a) {
		String addr = a.getHostAddress();
		int percent = addr.indexOf('%');
		if (percent == -1) return addr;
		return addr.substring(0, percent);
	}

	private void start() {
		GatewayDiscover d = new GatewayDiscover();
		try {
			d.discover();
		} catch (IOException | SAXException | ParserConfigurationException e) {
			logException(LOG, WARNING, e);
		}
		gateway = d.getValidGateway();
	}

	private void deleteMapping(int port) {
		try {
			gateway.deletePortMapping(port, "TCP");
			if (LOG.isLoggable(INFO))
				LOG.info("Deleted mapping for port " + port);
		} catch (IOException | SAXException e) {
			logException(LOG, WARNING, e);
		}
	}
}
