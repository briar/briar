package org.briarproject.sync;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import org.briarproject.api.Author;
import org.briarproject.api.AuthorFactory;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.ObjectReader;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.MessageVerifier;
import org.briarproject.api.sync.MessagingSessionFactory;
import org.briarproject.api.sync.PacketReaderFactory;
import org.briarproject.api.sync.PacketWriterFactory;
import org.briarproject.api.sync.SubscriptionUpdate;
import org.briarproject.api.sync.UnverifiedMessage;

import javax.inject.Singleton;

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
	ObjectReader<Author> getAuthorReader(CryptoComponent crypto) {
		return new AuthorReader(crypto);
	}

	@Provides
	ObjectReader<Group> getGroupReader(CryptoComponent crypto) {
		return new GroupReader(crypto);
	}

	@Provides
	ObjectReader<UnverifiedMessage> getMessageReader(
			ObjectReader<Group> groupReader,
			ObjectReader<Author> authorReader) {
		return new org.briarproject.sync.MessageReader(groupReader, authorReader);
	}

	@Provides
	ObjectReader<SubscriptionUpdate> getSubscriptionUpdateReader(
			ObjectReader<Group> groupReader) {
		return new SubscriptionUpdateReader(groupReader);
	}
}
