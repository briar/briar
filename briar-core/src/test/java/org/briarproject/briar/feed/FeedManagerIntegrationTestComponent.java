package org.briarproject.briar.feed;

import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.client.ClientModule;
import org.briarproject.bramble.contact.ContactModule;
import org.briarproject.bramble.crypto.CryptoExecutorModule;
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
import org.briarproject.bramble.test.TestSecureRandomModule;
import org.briarproject.bramble.test.TestSocksModule;
import org.briarproject.bramble.transport.TransportModule;
import org.briarproject.bramble.versioning.VersioningModule;
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
		TestSecureRandomModule.class,
		TestSocksModule.class,
		TestDnsModule.class,
		BriarClientModule.class,
		ClientModule.class,
		ContactModule.class,
		CryptoModule.class,
		CryptoExecutorModule.class,
		BlogModule.class,
		FeedModule.class,
		DataModule.class,
		DatabaseModule.class,
		EventModule.class,
		IdentityModule.class,
		LifecycleModule.class,
		SyncModule.class,
		SystemModule.class,
		TransportModule.class,
		VersioningModule.class
})
interface FeedManagerIntegrationTestComponent {

	void inject(FeedManagerIntegrationTest testCase);

	void inject(BlogModule.EagerSingletons init);

	void inject(ContactModule.EagerSingletons init);

	void inject(CryptoExecutorModule.EagerSingletons init);

	void inject(FeedModule.EagerSingletons init);

	void inject(IdentityModule.EagerSingletons init);

	void inject(LifecycleModule.EagerSingletons init);

	void inject(SyncModule.EagerSingletons init);

	void inject(SystemModule.EagerSingletons init);

	void inject(TransportModule.EagerSingletons init);

	void inject(VersioningModule.EagerSingletons init);

	LifecycleManager getLifecycleManager();

	FeedManager getFeedManager();

	BlogManager getBlogManager();

}
