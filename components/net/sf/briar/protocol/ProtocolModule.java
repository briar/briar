package net.sf.briar.protocol;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.protocol.AuthorFactory;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupFactory;
import net.sf.briar.api.protocol.MessageFactory;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.PacketFactory;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.ProtocolWriterFactory;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.protocol.UnverifiedBatch;
import net.sf.briar.api.serial.ObjectReader;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class ProtocolModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(AuthorFactory.class).to(AuthorFactoryImpl.class);
		bind(GroupFactory.class).to(GroupFactoryImpl.class);
		bind(MessageFactory.class).to(MessageFactoryImpl.class);
		bind(PacketFactory.class).to(PacketFactoryImpl.class);
		bind(ProtocolReaderFactory.class).to(ProtocolReaderFactoryImpl.class);
		bind(ProtocolWriterFactory.class).to(ProtocolWriterFactoryImpl.class);
		bind(UnverifiedBatchFactory.class).to(UnverifiedBatchFactoryImpl.class);
	}

	@Provides
	ObjectReader<Ack> getAckReader(PacketFactory ackFactory) {
		return new AckReader(ackFactory);
	}

	@Provides
	ObjectReader<Author> getAuthorReader(CryptoComponent crypto,
			AuthorFactory authorFactory) {
		return new AuthorReader(crypto, authorFactory);
	}

	@Provides
	ObjectReader<UnverifiedBatch> getBatchReader(CryptoComponent crypto,
			ObjectReader<UnverifiedMessage> messageReader,
			UnverifiedBatchFactory batchFactory) {
		return new BatchReader(crypto, messageReader, batchFactory);
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
	ObjectReader<UnverifiedMessage> getMessageReader(
			ObjectReader<MessageId> messageIdReader,
			ObjectReader<Group> groupReader,
			ObjectReader<Author> authorReader) {
		return new MessageReader(messageIdReader, groupReader, authorReader);
	}

	@Provides
	ObjectReader<Offer> getOfferReader(ObjectReader<MessageId> messageIdReader,
			PacketFactory packetFactory) {
		return new OfferReader(messageIdReader, packetFactory);
	}

	@Provides
	ObjectReader<Request> getRequestReader(PacketFactory packetFactory) {
		return new RequestReader(packetFactory);
	}

	@Provides
	ObjectReader<SubscriptionUpdate> getSubscriptionReader(
			ObjectReader<Group> groupReader, PacketFactory packetFactory) {
		return new SubscriptionUpdateReader(groupReader, packetFactory);
	}

	@Provides
	ObjectReader<TransportUpdate> getTransportReader(
			PacketFactory packetFactory) {
		return new TransportUpdateReader(packetFactory);
	}
}
