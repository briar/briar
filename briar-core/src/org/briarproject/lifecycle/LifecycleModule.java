package org.briarproject.lifecycle;

import javax.inject.Singleton;

import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.lifecycle.ShutdownManager;

import com.google.inject.AbstractModule;

public class LifecycleModule extends AbstractModule {

	protected void configure() {
		bind(LifecycleManager.class).to(
				LifecycleManagerImpl.class).in(Singleton.class);
		bind(ShutdownManager.class).to(
				ShutdownManagerImpl.class).in(Singleton.class);
	}
}
