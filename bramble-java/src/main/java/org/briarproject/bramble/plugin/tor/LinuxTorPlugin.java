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
import java.security.CodeSource;
import java.util.concurrent.Executor;

import javax.net.SocketFactory;

@NotNullByDefault
class LinuxTorPlugin extends TorPlugin {

	LinuxTorPlugin(Executor ioExecutor, NetworkManager networkManager,
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
		CodeSource codeSource =
				getClass().getProtectionDomain().getCodeSource();
		if (codeSource == null) throw new AssertionError("CodeSource null");
		try {
			URI path = codeSource.getLocation().toURI();
			File file = new File(path);
			return file.lastModified();
		} catch (URISyntaxException e) {
			throw new AssertionError(e);
		}
	}

}
