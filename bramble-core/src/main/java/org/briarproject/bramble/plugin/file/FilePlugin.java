package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.TransportConnectionReader;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;
import org.briarproject.bramble.api.plugin.simplex.SimplexPlugin;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.nullsafety.NotNullByDefault;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;
import static org.briarproject.bramble.api.plugin.file.FileConstants.PROP_PATH;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.StringUtils.isNullOrEmpty;

@NotNullByDefault
abstract class FilePlugin implements SimplexPlugin {

	private static final Logger LOG =
			getLogger(FilePlugin.class.getName());

	protected final PluginCallback callback;
	protected final long maxLatency;

	FilePlugin(PluginCallback callback, long maxLatency) {
		this.callback = callback;
		this.maxLatency = maxLatency;
	}

	@Override
	public long getMaxLatency() {
		return maxLatency;
	}

	@Override
	public TransportConnectionReader createReader(TransportProperties p) {
		if (getState() != ACTIVE) return null;
		String path = p.get(PROP_PATH);
		if (isNullOrEmpty(path)) return null;
		try {
			FileInputStream in = new FileInputStream(path);
			return new TransportInputStreamReader(in);
		} catch (IOException e) {
			logException(LOG, WARNING, e);
			return null;
		}
	}

	@Override
	public TransportConnectionWriter createWriter(TransportProperties p) {
		if (getState() != ACTIVE) return null;
		String path = p.get(PROP_PATH);
		if (isNullOrEmpty(path)) return null;
		try {
			File file = new File(path);
			if (!file.exists() && !file.createNewFile()) {
				LOG.info("Failed to create file");
				return null;
			}
			OutputStream out = new FileOutputStream(file);
			return new TransportOutputStreamWriter(this, out);
		} catch (IOException e) {
			logException(LOG, WARNING, e);
			return null;
		}
	}
}
