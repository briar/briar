package org.briarproject.system;

import org.briarproject.api.system.FileUtils;

import com.google.inject.AbstractModule;

public class DesktopOsModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(FileUtils.class).to(FileUtilsImpl.class);
	}
}
