package org.briarproject.briar.feed;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
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
			LifecycleManager lifecycleManager, EventBus eventBus) {

		lifecycleManager.registerClient(feedManager);
		eventBus.addListener(feedManager);
		return feedManager;
	}

}
