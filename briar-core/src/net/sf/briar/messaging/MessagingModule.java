package net.sf.briar.messaging;

import net.sf.briar.api.Author;
import net.sf.briar.api.AuthorFactory;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupFactory;
import net.sf.briar.api.messaging.MessageFactory;
import net.sf.briar.api.messaging.MessageVerifier;
import net.sf.briar.api.messaging.PacketReaderFactory;
import net.sf.briar.api.messaging.PacketWriterFactory;
import net.sf.briar.api.messaging.SubscriptionUpdate;
import net.sf.briar.api.messaging.UnverifiedMessage;
import net.sf.briar.api.serial.StructReader;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class MessagingModule extends AbstractModule {

	protected void configure() {
		bind(AuthorFactory.class).to(AuthorFactoryImpl.class);
		bind(GroupFactory.class).to(GroupFactoryImpl.class);
		bind(MessageFactory.class).to(MessageFactoryImpl.class);
		bind(MessageVerifier.class).to(MessageVerifierImpl.class);
		bind(PacketReaderFactory.class).to(PacketReaderFactoryImpl.class);
		bind(PacketWriterFactory.class).to(PacketWriterFactoryImpl.class);
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
