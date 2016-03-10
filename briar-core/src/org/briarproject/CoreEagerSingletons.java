package org.briarproject;

import org.briarproject.contact.ContactModule;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.db.DatabaseModule;
import org.briarproject.forum.ForumModule;
import org.briarproject.lifecycle.LifecycleModule;
import org.briarproject.messaging.MessagingModule;
import org.briarproject.plugins.PluginsModule;
import org.briarproject.properties.PropertiesModule;
import org.briarproject.sync.SyncModule;
import org.briarproject.transport.TransportModule;

public class CoreEagerSingletons {

	public static void initEagerSingletons(CoreComponent c) {
		c.inject(new ContactModule.EagerSingletons());
		c.inject(new CryptoModule.EagerSingletons());
		c.inject(new DatabaseModule.EagerSingletons());
		c.inject(new ForumModule.EagerSingletons());
		c.inject(new LifecycleModule.EagerSingletons());
		c.inject(new MessagingModule.EagerSingletons());
		c.inject(new PluginsModule.EagerSingletons());
		c.inject(new PropertiesModule.EagerSingletons());
		c.inject(new SyncModule.EagerSingletons());
		c.inject(new TransportModule.EagerSingletons());
	}
}
