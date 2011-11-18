package net.sf.briar.lifecycle;

import net.sf.briar.api.lifecycle.ShutdownManager;
import net.sf.briar.util.OsUtils;

import com.google.inject.AbstractModule;

public class LifecycleModule extends AbstractModule {

	@Override
	protected void configure() {
		if(OsUtils.isWindows())
			bind(ShutdownManager.class).to(WindowsShutdownManagerImpl.class);
		else bind(ShutdownManager.class).to(ShutdownManagerImpl.class);
	}
}
