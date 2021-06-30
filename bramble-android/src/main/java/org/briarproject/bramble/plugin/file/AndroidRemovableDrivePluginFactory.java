package org.briarproject.bramble.plugin.file;

import android.app.Application;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.simplex.SimplexPlugin;
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginFactory;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static java.util.concurrent.TimeUnit.DAYS;
import static org.briarproject.bramble.api.plugin.file.RemovableDriveConstants.ID;

@Immutable
@NotNullByDefault
public class AndroidRemovableDrivePluginFactory implements
		SimplexPluginFactory {

	private static final int MAX_LATENCY = (int) DAYS.toMillis(14);

	private final Application app;

	@Inject
	AndroidRemovableDrivePluginFactory(Application app) {
		this.app = app;
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
		return new AndroidRemovableDrivePlugin(app, callback, MAX_LATENCY);
	}
}
