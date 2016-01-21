package org.briarproject;

import org.briarproject.contact.ContactModule;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.db.DatabaseModule;
import org.briarproject.forum.ForumModule;
import org.briarproject.introduction.IntroductionModule;
import org.briarproject.lifecycle.LifecycleModule;
import org.briarproject.messaging.MessagingModule;
import org.briarproject.plugins.PluginsModule;
import org.briarproject.properties.PropertiesModule;
import org.briarproject.sync.SyncModule;
import org.briarproject.transport.TransportModule;

public interface CoreEagerSingletons {
	void inject(ContactModule.EagerSingletons init);
	void inject(CryptoModule.EagerSingletons init);
	void inject(DatabaseModule.EagerSingletons init);
	void inject(ForumModule.EagerSingletons init);
	void inject(IntroductionModule.EagerSingletons init);
	void inject(LifecycleModule.EagerSingletons init);
	void inject(MessagingModule.EagerSingletons init);
	void inject(PluginsModule.EagerSingletons init);
	void inject(PropertiesModule.EagerSingletons init);
	void inject(SyncModule.EagerSingletons init);
	void inject(TransportModule.EagerSingletons init);

}
