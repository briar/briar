package org.briarproject.bramble.plugin.tor;

import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginCallback;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.api.system.ResourceProvider;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Executor;

import javax.net.SocketFactory;

@NotNullByDefault
class JavaTorPlugin extends TorPlugin {

	JavaTorPlugin(Executor ioExecutor, NetworkManager networkManager,
			LocationUtils locationUtils, SocketFactory torSocketFactory,
			Clock clock, ResourceProvider resourceProvider,
			CircumventionProvider circumventionProvider, Backoff backoff,
			DuplexPluginCallback callback, String architecture, int maxLatency,
			int maxIdleTime, File torDirectory) {
		super(ioExecutor, networkManager, locationUtils, torSocketFactory,
				clock, resourceProvider, circumventionProvider, backoff,
				callback, architecture, maxLatency, maxIdleTime, torDirectory);
	}

	@Override
	protected int getProcessId() {
		try {
			// Java 9: ProcessHandle.current().pid()
			return Integer.parseInt(
					new File("/proc/self").getCanonicalFile().getName());
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	@Override
	protected long getLastUpdateTime() {
		try {
			URI path = getClass().getProtectionDomain().getCodeSource()
					.getLocation().toURI();
			return new File(path).lastModified();
		} catch (URISyntaxException e) {
			throw new AssertionError(e);
		}
	}

}
