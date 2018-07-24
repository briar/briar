package org.briarproject.briar.android;

import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.briar.BriarCoreModule;

public class BriarTestApplication extends BriarApplicationImpl {

	@Override
	protected AndroidComponent createApplicationComponent() {
		AndroidComponent component = DaggerBriarTestComponent.builder()
				.appModule(new AppModule(this)).build();
		// We need to load the eager singletons directly after making the
		// dependency graphs
		BrambleCoreModule.initEagerSingletons(component);
		BriarCoreModule.initEagerSingletons(component);
		AndroidEagerSingletons.initEagerSingletons(component);
		return component;
	}

}
