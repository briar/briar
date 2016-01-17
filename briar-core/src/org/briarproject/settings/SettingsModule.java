package org.briarproject.settings;

import com.google.inject.AbstractModule;

import org.briarproject.api.settings.SettingsManager;

public class SettingsModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(SettingsManager.class).to(SettingsManagerImpl.class);
	}
}
