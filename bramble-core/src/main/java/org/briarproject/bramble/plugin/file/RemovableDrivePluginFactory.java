package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.simplex.SimplexPlugin;
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginFactory;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static java.util.concurrent.TimeUnit.DAYS;
import static org.briarproject.bramble.api.plugin.RemovableDriveConstants.ID;

@Immutable
@NotNullByDefault
public class RemovableDrivePluginFactory implements SimplexPluginFactory {

	private final int MAX_LATENCY = (int) DAYS.toMillis(14);

	@Inject
	RemovableDrivePluginFactory() {
	}

	@Override
	public TransportId getId() {
		return ID;
	}

	@Override
	public int getMaxLatency() {
		return MAX_LATENCY;
	}

	@Nullable
	@Override
	public SimplexPlugin createPlugin(PluginCallback callback) {
		return new RemovableDrivePlugin(MAX_LATENCY);
	}
}
