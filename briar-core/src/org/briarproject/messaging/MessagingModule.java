package org.briarproject.messaging;

import javax.inject.Singleton;

import org.briarproject.api.Author;
import org.briarproject.api.AuthorFactory;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.messaging.Group;
import org.briarproject.api.messaging.GroupFactory;
import org.briarproject.api.messaging.MessageFactory;
import org.briarproject.api.messaging.MessageVerifier;
import org.briarproject.api.messaging.MessagingSessionFactory;
import org.briarproject.api.messaging.PacketReaderFactory;
import org.briarproject.api.messaging.PacketWriterFactory;
import org.briarproject.api.messaging.SubscriptionUpdate;
import org.briarproject.api.messaging.UnverifiedMessage;
import org.briarproject.api.serial.StructReader;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class MessagingModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(AuthorFactory.class).to(AuthorFactoryImpl.class);
		bind(GroupFactory.class).to(GroupFactoryImpl.class);
		bind(MessageFactory.class).to(MessageFactoryImpl.class);
		bind(MessageVerifier.class).to(MessageVerifierImpl.class);
		bind(PacketReaderFactory.class).to(PacketReaderFactoryImpl.class);
		bind(PacketWriterFactory.class).to(PacketWriterFactoryImpl.class);
		bind(MessagingSessionFactory.class).to(
				MessagingSessionFactoryImpl.class).in(Singleton.class);
	}

	@Provides
	StructReader<Author> getAuthorReader(CryptoComponent crypto) {
		return new AuthorReader(crypto);
	}

	@Provides
	StructReader<Group> getGroupReader(CryptoComponent crypto) {
		return new GroupReader(crypto);
	}

	@Provides
	StructReader<UnverifiedMessage> getMessageReader(
			StructReader<Group> groupReader,
			StructReader<Author> authorReader) {
		return new MessageReader(groupReader, authorReader);
	}

	@Provides
	StructReader<SubscriptionUpdate> getSubscriptionUpdateReader(
			StructReader<Group> groupReader) {
		return new SubscriptionUpdateReader(groupReader);
	}
}
