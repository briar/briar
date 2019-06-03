package org.briarproject.briar.feed;

import org.briarproject.bramble.BrambleCoreEagerSingletons;
import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.test.BrambleCoreIntegrationTestModule;
import org.briarproject.bramble.test.TestSocksModule;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.feed.FeedManager;
import org.briarproject.briar.blog.BlogModule;
import org.briarproject.briar.client.BriarClientModule;
import org.briarproject.briar.test.TestDnsModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		BrambleCoreIntegrationTestModule.class,
		BrambleCoreModule.class,
		BlogModule.class,
		BriarClientModule.class,
		FeedModule.class,
		TestDnsModule.class,
		TestSocksModule.class,
})
interface FeedManagerIntegrationTestComponent
		extends BrambleCoreEagerSingletons {

	void inject(FeedManagerIntegrationTest testCase);

	void inject(BlogModule.EagerSingletons init);

	void inject(FeedModule.EagerSingletons init);

	default void injectFeedManagerEagerSingletons() {
		injectBrambleCoreEagerSingletons();
		inject(new BlogModule.EagerSingletons());
		inject(new FeedModule.EagerSingletons());
	}

	IdentityManager getIdentityManager();

	LifecycleManager getLifecycleManager();

	FeedManager getFeedManager();

	BlogManager getBlogManager();

}
