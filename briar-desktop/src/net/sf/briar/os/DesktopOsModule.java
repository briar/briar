package net.sf.briar.os;

import net.sf.briar.api.os.FileUtils;

import com.google.inject.AbstractModule;

public class DesktopOsModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(FileUtils.class).to(FileUtilsImpl.class);
	}
}
