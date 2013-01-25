package net.sf.briar.protocol;

import java.io.InputStream;

import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.ProtocolReader;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionAck;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportAck;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.protocol.UnverifiedMessage;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.StructReader;

import com.google.inject.Inject;
import com.google.inject.Provider;

// FIXME: Refactor this package to reduce boilerplate
class ProtocolReaderFactoryImpl implements ProtocolReaderFactory {

	private final ReaderFactory readerFactory;
	private final Provider<StructReader<Ack>> ackProvider;
	private final Provider<StructReader<UnverifiedMessage>> messageProvider;
	private final Provider<StructReader<Offer>> offerProvider;
	private final Provider<StructReader<Request>> requestProvider;
	private final Provider<StructReader<SubscriptionAck>> subscriptionAckProvider;
	private final Provider<StructReader<SubscriptionUpdate>> subscriptionUpdateProvider;
	private final Provider<StructReader<TransportAck>> transportAckProvider;
	private final Provider<StructReader<TransportUpdate>> transportUpdateProvider;

	@Inject
	ProtocolReaderFactoryImpl(ReaderFactory readerFactory,
			Provider<StructReader<Ack>> ackProvider,
			Provider<StructReader<UnverifiedMessage>> messageProvider,
			Provider<StructReader<Offer>> offerProvider,
			Provider<StructReader<Request>> requestProvider,
			Provider<StructReader<SubscriptionAck>> subscriptionAckProvider,
			Provider<StructReader<SubscriptionUpdate>> subscriptionUpdateProvider,
			Provider<StructReader<TransportAck>> transportAckProvider,
			Provider<StructReader<TransportUpdate>> transportUpdateProvider) {
		this.readerFactory = readerFactory;
		this.ackProvider = ackProvider;
		this.messageProvider = messageProvider;
		this.offerProvider = offerProvider;
		this.requestProvider = requestProvider;
		this.subscriptionAckProvider = subscriptionAckProvider;
		this.subscriptionUpdateProvider = subscriptionUpdateProvider;
		this.transportAckProvider = transportAckProvider;
		this.transportUpdateProvider = transportUpdateProvider;
	}

	public ProtocolReader createProtocolReader(InputStream in) {
		return new ProtocolReaderImpl(in, readerFactory, ackProvider.get(),
				messageProvider.get(), offerProvider.get(),
				requestProvider.get(), subscriptionAckProvider.get(),
				subscriptionUpdateProvider.get(), transportAckProvider.get(),
				transportUpdateProvider.get());
	}
}
