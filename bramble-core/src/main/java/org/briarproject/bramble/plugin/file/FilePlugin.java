package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.TransportConnectionReader;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;
import org.briarproject.bramble.api.plugin.simplex.SimplexPlugin;
import org.briarproject.bramble.api.properties.TransportProperties;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.FileConstants.PROP_PATH;
import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.StringUtils.isNullOrEmpty;

@NotNullByDefault
abstract class FilePlugin implements SimplexPlugin {

	private static final Logger LOG =
			getLogger(FilePlugin.class.getName());

	protected final PluginCallback callback;
	protected final int maxLatency;

	protected abstract void writerFinished(File f, boolean exception);

	protected abstract void readerFinished(File f, boolean exception,
			boolean recognised);

	FilePlugin(PluginCallback callback, int maxLatency) {
		this.callback = callback;
		this.maxLatency = maxLatency;
	}

	@Override
	public int getMaxLatency() {
		return maxLatency;
	}

	@Override
	public TransportConnectionReader createReader(TransportProperties p) {
		if (getState() != ACTIVE) return null;
		String path = p.get(PROP_PATH);
		if (isNullOrEmpty(path)) return null;
		try {
			File file = new File(path);
			FileInputStream in = new FileInputStream(file);
			return new FileTransportReader(file, in, this);
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
			FileOutputStream out = new FileOutputStream(file);
			return new FileTransportWriter(file, out, this);
		} catch (IOException e) {
			logException(LOG, WARNING, e);
			return null;
		}
	}
}
