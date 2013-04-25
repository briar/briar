package net.sf.briar.android;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.sf.briar.api.android.AndroidExecutor;
import net.sf.briar.api.android.BundleEncrypter;
import net.sf.briar.api.android.DatabaseUiExecutor;
import net.sf.briar.api.android.ReferenceManager;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.lifecycle.ShutdownManager;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPluginConfig;
import net.sf.briar.api.plugins.duplex.DuplexPluginFactory;
import net.sf.briar.api.plugins.simplex.SimplexPluginConfig;
import net.sf.briar.api.plugins.simplex.SimplexPluginFactory;
import net.sf.briar.plugins.droidtooth.DroidtoothPluginFactory;
import net.sf.briar.plugins.tcp.LanTcpPluginFactory;
import net.sf.briar.plugins.tcp.WanTcpPluginFactory;
import net.sf.briar.plugins.tor.TorPluginFactory;
import android.content.Context;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class AndroidModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(AndroidExecutor.class).to(AndroidExecutorImpl.class);
		bind(BundleEncrypter.class).to(BundleEncrypterImpl.class).in(
				Singleton.class);
		bind(ReferenceManager.class).to(ReferenceManagerImpl.class).in(
				Singleton.class);
		// Use a single thread so DB accesses from the UI don't overlap, with
		// an unbounded queue so submissions don't block
		bind(Executor.class).annotatedWith(DatabaseUiExecutor.class).toInstance(
				Executors.newSingleThreadExecutor());
	}

	@Provides
	SimplexPluginConfig getSimplexPluginConfig(
			@PluginExecutor ExecutorService pluginExecutor) {
		return new SimplexPluginConfig() {
			public Collection<SimplexPluginFactory> getFactories() {
				return Collections.emptyList();
			}
		};
	}

	@Provides
	DuplexPluginConfig getDuplexPluginConfig(
			@PluginExecutor ExecutorService pluginExecutor,
			AndroidExecutor androidExecutor, Context appContext,
			CryptoComponent crypto, ShutdownManager shutdownManager) {
		DuplexPluginFactory droidtooth = new DroidtoothPluginFactory(
				pluginExecutor, androidExecutor, appContext,
				crypto.getSecureRandom());
		DuplexPluginFactory tor = new TorPluginFactory(pluginExecutor,
				appContext);
		DuplexPluginFactory lan = new LanTcpPluginFactory(pluginExecutor);
		DuplexPluginFactory wan = new WanTcpPluginFactory(pluginExecutor,
				shutdownManager);
		final Collection<DuplexPluginFactory> factories =
				Arrays.asList(droidtooth, tor, lan, wan);
		return new DuplexPluginConfig() {
			public Collection<DuplexPluginFactory> getFactories() {
				return factories;
			}
		};
	}
}
