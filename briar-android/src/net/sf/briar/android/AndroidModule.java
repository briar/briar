package net.sf.briar.android;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import net.sf.briar.api.android.AndroidExecutor;
import net.sf.briar.api.android.BundleEncrypter;
import net.sf.briar.api.android.DatabaseUiExecutor;
import net.sf.briar.api.android.ReferenceManager;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class AndroidModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(AndroidExecutor.class).to(AndroidExecutorImpl.class);
		bind(BundleEncrypter.class).to(BundleEncrypterImpl.class).in(
			Singleton.class);
		bind(ReferenceManager.class).to(ReferenceManagerImpl.class).in(
				Singleton.class);
		// Use a single thread so DB accesses from the UI don't overlap
		bind(Executor.class).annotatedWith(DatabaseUiExecutor.class).toInstance(
				Executors.newSingleThreadExecutor());
	}
}
