package org.briarproject.briar.feed;

import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.client.ClientModule;
import org.briarproject.bramble.contact.ContactModule;
import org.briarproject.bramble.crypto.CryptoModule;
import org.briarproject.bramble.data.DataModule;
import org.briarproject.bramble.db.DatabaseModule;
import org.briarproject.bramble.event.EventModule;
import org.briarproject.bramble.identity.IdentityModule;
import org.briarproject.bramble.lifecycle.LifecycleModule;
import org.briarproject.bramble.sync.SyncModule;
import org.briarproject.bramble.system.SystemModule;
import org.briarproject.bramble.test.TestDatabaseModule;
import org.briarproject.bramble.test.TestPluginConfigModule;
import org.briarproject.bramble.test.TestSeedProviderModule;
import org.briarproject.bramble.test.TestSocksModule;
import org.briarproject.bramble.transport.TransportModule;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.feed.FeedManager;
import org.briarproject.briar.blog.BlogModule;
import org.briarproject.briar.client.BriarClientModule;
import org.briarproject.briar.test.TestDnsModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		TestDatabaseModule.class,
		TestPluginConfigModule.class,
		TestSeedProviderModule.class,
		TestSocksModule.class,
		TestDnsModule.class,
		LifecycleModule.class,
		BriarClientModule.class,
		ClientModule.class,
		ContactModule.class,
		CryptoModule.class,
		BlogModule.class,
		FeedModule.class,
		DataModule.class,
		DatabaseModule.class,
		EventModule.class,
		IdentityModule.class,
		SyncModule.class,
		SystemModule.class,
		TransportModule.class
})
interface FeedManagerIntegrationTestComponent {

	void inject(FeedManagerIntegrationTest testCase);

	void inject(FeedModule.EagerSingletons init);

	void inject(BlogModule.EagerSingletons init);

	void inject(ContactModule.EagerSingletons init);

	void inject(CryptoModule.EagerSingletons init);

	void inject(IdentityModule.EagerSingletons init);

	void inject(LifecycleModule.EagerSingletons init);

	void inject(SyncModule.EagerSingletons init);

	void inject(SystemModule.EagerSingletons init);

	void inject(TransportModule.EagerSingletons init);

	LifecycleManager getLifecycleManager();

	FeedManager getFeedManager();

	BlogManager getBlogManager();

}
