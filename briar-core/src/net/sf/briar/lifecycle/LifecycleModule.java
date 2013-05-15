package net.sf.briar.lifecycle;

import net.sf.briar.api.lifecycle.LifecycleManager;
import net.sf.briar.api.lifecycle.ShutdownManager;
import net.sf.briar.util.OsUtils;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class LifecycleModule extends AbstractModule {

	protected void configure() {
		bind(LifecycleManager.class).to(
				LifecycleManagerImpl.class).in(Singleton.class);
		if(OsUtils.isWindows())
			bind(ShutdownManager.class).to(WindowsShutdownManagerImpl.class);
		else bind(ShutdownManager.class).to(ShutdownManagerImpl.class);
	}
}
