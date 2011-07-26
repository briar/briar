package net.sf.briar.protocol;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.protocol.AuthorFactory;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupFactory;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageEncoder;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.serial.ObjectReader;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class ProtocolModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(AckFactory.class).to(AckFactoryImpl.class);
		bind(AuthorFactory.class).to(AuthorFactoryImpl.class);
		bind(BatchFactory.class).to(BatchFactoryImpl.class);
		bind(GroupFactory.class).to(GroupFactoryImpl.class);
		bind(OfferFactory.class).to(OfferFactoryImpl.class);
		bind(SubscriptionFactory.class).to(SubscriptionFactoryImpl.class);
		bind(TransportFactory.class).to(TransportFactoryImpl.class);
		bind(MessageEncoder.class).to(MessageEncoderImpl.class);
	}

	@Provides
	ObjectReader<BatchId> getBatchIdReader() {
		return new BatchIdReader();
	}

	@Provides
	ObjectReader<MessageId> getMessageIdReader() {
		return new MessageIdReader();
	}

	@Provides
	ObjectReader<Group> getGroupReader(CryptoComponent crypto,
			GroupFactory groupFactory) {
		return new GroupReader(crypto, groupFactory);
	}

	@Provides
	ObjectReader<Author> getAuthorReader(CryptoComponent crypto,
			AuthorFactory authorFactory) {
		return new AuthorReader(crypto, authorFactory);
	}

	@Provides
	ObjectReader<Message> getMessageReader(CryptoComponent crypto,
			ObjectReader<MessageId> messageIdReader,
			ObjectReader<Group> groupReader,
			ObjectReader<Author> authorReader) {
		return new MessageReader(crypto, messageIdReader, groupReader,
				authorReader);
	}
}
