package org.briarproject.briar.feed;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.feed.FeedManager;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.net.SocketFactory;

import dagger.Module;
import dagger.Provides;
import okhttp3.Dns;
import okhttp3.OkHttpClient;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Module
public class FeedModule {

	private static final int CONNECT_TIMEOUT = 60_000; // Milliseconds

	public static class EagerSingletons {
		@Inject
		FeedManager feedManager;
	}

	@Provides
	@Singleton
	FeedManager provideFeedManager(FeedManagerImpl feedManager,
			LifecycleManager lifecycleManager, EventBus eventBus,
			BlogManager blogManager) {
		lifecycleManager.registerOpenDatabaseHook(feedManager);
		eventBus.addListener(feedManager);
		blogManager.registerRemoveBlogHook(feedManager);
		return feedManager;
	}

	@Provides
	FeedFactory provideFeedFactory(FeedFactoryImpl feedFactory) {
		return feedFactory;
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
