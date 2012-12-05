package net.sf.briar.android;

import net.sf.briar.api.android.AndroidExecutor;

import com.google.inject.AbstractModule;

public class AndroidModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(AndroidExecutor.class).to(AndroidExecutorImpl.class);
	}
}
