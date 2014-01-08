package net.sf.briar.system;

import net.sf.briar.api.system.FileUtils;

import com.google.inject.AbstractModule;

public class DesktopOsModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(FileUtils.class).to(FileUtilsImpl.class);
	}
}
