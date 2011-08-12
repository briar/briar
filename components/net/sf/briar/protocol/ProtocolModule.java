package net.sf.briar.protocol;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.protocol.AuthorFactory;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupFactory;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageEncoder;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportUpdate;
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
		bind(RequestFactory.class).to(RequestFactoryImpl.class);
		bind(SubscriptionFactory.class).to(SubscriptionFactoryImpl.class);
		bind(TransportFactory.class).to(TransportFactoryImpl.class);
		bind(MessageEncoder.class).to(MessageEncoderImpl.class);
	}

	@Provides
	ObjectReader<Ack> getAckReader(ObjectReader<BatchId> batchIdReader,
			AckFactory ackFactory) {
		return new AckReader(batchIdReader, ackFactory);
	}

	@Provides
	ObjectReader<Author> getAuthorReader(CryptoComponent crypto,
			AuthorFactory authorFactory) {
		return new AuthorReader(crypto, authorFactory);
	}

	@Provides
	ObjectReader<Batch> getBatchReader(CryptoComponent crypto,
			ObjectReader<Message> messageReader, BatchFactory batchFactory) {
		return new BatchReader(crypto, messageReader, batchFactory);
	}

	@Provides
	ObjectReader<BatchId> getBatchIdReader() {
		return new BatchIdReader();
	}

	@Provides
	ObjectReader<Group> getGroupReader(CryptoComponent crypto,
			GroupFactory groupFactory) {
		return new GroupReader(crypto, groupFactory);
	}

	@Provides
	ObjectReader<MessageId> getMessageIdReader() {
		return new MessageIdReader();
	}

	@Provides
	ObjectReader<Message> getMessageReader(CryptoComponent crypto,
			ObjectReader<MessageId> messageIdReader,
			ObjectReader<Group> groupReader,
			ObjectReader<Author> authorReader) {
		return new MessageReader(crypto, messageIdReader, groupReader,
				authorReader);
	}

	@Provides
	ObjectReader<Offer> getOfferReader(ObjectReader<MessageId> messageIdReader,
			OfferFactory offerFactory) {
		return new OfferReader(messageIdReader, offerFactory);
	}

	@Provides
	ObjectReader<Request> getRequestReader(RequestFactory requestFactory) {
		return new RequestReader(requestFactory);
	}

	@Provides
	ObjectReader<SubscriptionUpdate> getSubscriptionReader(
			ObjectReader<Group> groupReader,
			SubscriptionFactory subscriptionFactory) {
		return new SubscriptionReader(groupReader, subscriptionFactory);
	}

	@Provides
	ObjectReader<TransportUpdate> getTransportReader(
			TransportFactory transportFactory) {
		return new TransportReader(transportFactory);
	}
}
