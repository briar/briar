package org.briarproject.sync;

import org.briarproject.TestDatabaseModule;
import org.briarproject.TestLifecycleModule;
import org.briarproject.TestSystemModule;
import org.briarproject.contact.ContactModule;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.data.DataModule;
import org.briarproject.db.DatabaseModule;
import org.briarproject.event.EventModule;
import org.briarproject.forum.ForumModule;
import org.briarproject.identity.IdentityModule;
import org.briarproject.messaging.MessagingModule;
import org.briarproject.transport.TransportModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {TestDatabaseModule.class, TestLifecycleModule.class,
		TestSystemModule.class, ContactModule.class, CryptoModule.class,
		DatabaseModule.class, EventModule.class, SyncModule.class,
		DataModule.class, TransportModule.class, ForumModule.class,
		IdentityModule.class, MessagingModule.class})
public interface ConstantsComponent {
	void inject(ConstantsTest testCase);
}
