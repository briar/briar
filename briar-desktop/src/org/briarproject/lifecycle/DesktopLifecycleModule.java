package org.briarproject.lifecycle;

import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.lifecycle.ShutdownManager;
import org.briarproject.util.OsUtils;


public class DesktopLifecycleModule extends LifecycleModule {

	/*
	// TODO
	@Override
	protected void configure() {
		bind(LifecycleManager.class).to(
				LifecycleManagerImpl.class).in(Singleton.class);
		if (OsUtils.isWindows()) {
			bind(ShutdownManager.class).to(
					WindowsShutdownManagerImpl.class).in(Singleton.class);
		} else {
			bind(ShutdownManager.class).to(
					ShutdownManagerImpl.class).in(Singleton.class);
		}
	}
	*/
}
