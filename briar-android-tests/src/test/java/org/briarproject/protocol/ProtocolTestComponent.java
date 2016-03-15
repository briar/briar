package org.briarproject.protocol;

import org.briarproject.TestDatabaseModule;
import org.briarproject.TestSystemModule;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.data.DataModule;
import org.briarproject.db.DatabaseModule;
import org.briarproject.event.EventModule;
import org.briarproject.sync.SyncModule;
import org.briarproject.transport.TransportModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {TestDatabaseModule.class, TestSystemModule.class,
		CryptoModule.class, DatabaseModule.class, EventModule.class,
		SyncModule.class, DataModule.class, TransportModule.class})
public interface ProtocolTestComponent {
	void inject(ProtocolIntegrationTest testCase);
	GroupFactory getGroupFactory();
	MessageFactory getMessageFactory();
}
