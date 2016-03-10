package org.briarproject.system;


import android.app.Application;

import org.briarproject.api.system.Clock;
import org.briarproject.api.system.LocationUtils;
import org.briarproject.api.system.SeedProvider;
import org.briarproject.api.system.Timer;

import dagger.Module;
import dagger.Provides;

public class SystemModuleExtension extends SystemModule {

	private final Application app;

	public SystemModuleExtension(final Application app) {
		this.app = app;
	}

	@Override
	public SeedProvider provideSeedProvider() {
		return new AndroidSeedProvider(app);
	}

	@Override
	public LocationUtils provideLocationUtils() {
		return new AndroidLocationUtils(app);
	}

}
