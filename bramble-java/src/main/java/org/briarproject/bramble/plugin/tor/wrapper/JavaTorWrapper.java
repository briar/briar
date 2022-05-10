package org.briarproject.bramble.plugin.tor.wrapper;

import org.briarproject.nullsafety.NotNullByDefault;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.concurrent.Executor;

import static org.briarproject.nullsafety.NullSafety.requireNonNull;

@NotNullByDefault
abstract class JavaTorWrapper extends AbstractTorWrapper {

	JavaTorWrapper(Executor ioExecutor,
			Executor eventExecutor,
			String architecture,
			File torDirectory,
			int torSocksPort,
			int torControlPort) {
		super(ioExecutor, eventExecutor, architecture, torDirectory,
				torSocksPort, torControlPort);
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

	@Override
	protected InputStream getResourceInputStream(String name,
			String extension) {
		ClassLoader cl = getClass().getClassLoader();
		return requireNonNull(cl.getResourceAsStream(name + extension));
	}
}
