package net.sf.briar.protocol;

import java.io.InputStream;

import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.ProtocolReader;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.protocol.UnverifiedBatch;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.ReaderFactory;

import com.google.inject.Inject;
import com.google.inject.Provider;

class ProtocolReaderFactoryImpl implements ProtocolReaderFactory {

	private final ReaderFactory readerFactory;
	private final Provider<ObjectReader<Ack>> ackProvider;
	private final Provider<ObjectReader<UnverifiedBatch>> batchProvider;
	private final Provider<ObjectReader<Offer>> offerProvider;
	private final Provider<ObjectReader<Request>> requestProvider;
	private final Provider<ObjectReader<SubscriptionUpdate>> subscriptionProvider;
	private final Provider<ObjectReader<TransportUpdate>> transportProvider;

	@Inject
	ProtocolReaderFactoryImpl(ReaderFactory readerFactory,
			Provider<ObjectReader<Ack>> ackProvider,
			Provider<ObjectReader<UnverifiedBatch>> batchProvider,
			Provider<ObjectReader<Offer>> offerProvider,
			Provider<ObjectReader<Request>> requestProvider,
			Provider<ObjectReader<SubscriptionUpdate>> subscriptionProvider,
			Provider<ObjectReader<TransportUpdate>> transportProvider) {
		this.readerFactory = readerFactory;
		this.ackProvider = ackProvider;
		this.batchProvider = batchProvider;
		this.offerProvider = offerProvider;
		this.requestProvider = requestProvider;
		this.subscriptionProvider = subscriptionProvider;
		this.transportProvider = transportProvider;
	}

	public ProtocolReader createProtocolReader(InputStream in) {
		return new ProtocolReaderImpl(in, readerFactory, ackProvider.get(),
				batchProvider.get(), offerProvider.get(), requestProvider.get(),
				subscriptionProvider.get(), transportProvider.get());
	}
}
