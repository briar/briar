package org.briarproject.clients;

import com.google.inject.AbstractModule;

import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageQueueManager;
import org.briarproject.api.clients.PrivateGroupFactory;
import org.briarproject.api.clients.QueueMessageFactory;

public class ClientsModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ClientHelper.class).to(ClientHelperImpl.class);
		bind(MessageQueueManager.class).to(MessageQueueManagerImpl.class);
		bind(PrivateGroupFactory.class).to(PrivateGroupFactoryImpl.class);
		bind(QueueMessageFactory.class).to(QueueMessageFactoryImpl.class);
	}
}
