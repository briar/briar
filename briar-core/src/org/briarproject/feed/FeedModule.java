package org.briarproject.feed;

import org.briarproject.api.event.EventBus;
import org.briarproject.api.feed.FeedManager;
import org.briarproject.api.lifecycle.LifecycleManager;

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
			LifecycleManager lifecycleManager, EventBus eventBus) {

		lifecycleManager.registerClient(feedManager);
		eventBus.addListener(feedManager);
		return feedManager;
	}

}
