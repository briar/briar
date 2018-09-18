package org.briarproject.briar.android;

import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.briar.BriarCoreModule;

public class BriarTestComponentApplication extends BriarApplicationImpl {

	@Override
	protected AndroidComponent createApplicationComponent() {
		AndroidComponent component = DaggerBriarUiTestComponent.builder()
				.testAppModule(new TestAppModule(this)).build();
		// We need to load the eager singletons directly after making the
		// dependency graphs
		BrambleCoreModule.initEagerSingletons(component);
		BriarCoreModule.initEagerSingletons(component);
		AndroidEagerSingletons.initEagerSingletons(component);
		return component;
	}

}
