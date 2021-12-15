package org.briarproject.bramble.io;

import org.briarproject.bramble.api.WeakSingletonProvider;
import org.briarproject.bramble.api.io.TimeoutMonitor;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import javax.net.SocketFactory;

import dagger.Module;
import dagger.Provides;
import okhttp3.Dns;
import okhttp3.OkHttpClient;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Module
public class IoModule {

	private static final int CONNECT_TIMEOUT = 60_000; // Milliseconds

	@Provides
	@Singleton
	TimeoutMonitor provideTimeoutMonitor(TimeoutMonitorImpl timeoutMonitor) {
		return timeoutMonitor;
	}

	// Share an HTTP client instance between requests where possible, while
	// allowing the client to be garbage-collected between requests. The
	// provider keeps a weak reference to the last client instance and reuses
	// the instance until it gets garbage-collected. See
	// https://medium.com/@leandromazzuquini/if-you-are-using-okhttp-you-should-know-this-61d68e065a2b
	@Provides
	@Singleton
	WeakSingletonProvider<OkHttpClient> provideOkHttpClientProvider(
			SocketFactory torSocketFactory, Dns noDnsLookups) {
		return new WeakSingletonProvider<OkHttpClient>() {
			@Override
			@Nonnull
			public OkHttpClient createInstance() {
				return new OkHttpClient.Builder()
						.socketFactory(torSocketFactory)
						.dns(noDnsLookups) // Don't make local DNS lookups
						.connectTimeout(CONNECT_TIMEOUT, MILLISECONDS)
						.build();
			}
		};
	}
}
