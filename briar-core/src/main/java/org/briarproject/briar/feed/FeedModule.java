package org.briarproject.briar.feed;

import org.briarproject.bramble.api.FeatureFlags;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.feed.FeedManager;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class FeedModule {

	public static class EagerSingletons {
		@Inject
		FeedManager feedManager;
	}

	@Provides
	@Singleton
	FeedManager provideFeedManager(FeedManagerImpl feedManager,
			LifecycleManager lifecycleManager, EventBus eventBus,
			BlogManager blogManager, FeatureFlags featureFlags) {
		if (!featureFlags.shouldEnableBlogsInCore()) {
			return feedManager;
		}
		lifecycleManager.registerOpenDatabaseHook(feedManager);
		eventBus.addListener(feedManager);
		blogManager.registerRemoveBlogHook(feedManager);
		return feedManager;
	}

	@Provides
	FeedFactory provideFeedFactory(FeedFactoryImpl feedFactory) {
		return feedFactory;
	}
}
