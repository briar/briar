package net.sf.briar.lifecycle;

import javax.inject.Singleton;

import net.sf.briar.api.lifecycle.LifecycleManager;
import net.sf.briar.api.lifecycle.ShutdownManager;

import com.google.inject.AbstractModule;

public class LifecycleModule extends AbstractModule {

	protected void configure() {
		bind(LifecycleManager.class).to(
				LifecycleManagerImpl.class).in(Singleton.class);
		bind(ShutdownManager.class).to(
				ShutdownManagerImpl.class).in(Singleton.class);
	}
}
