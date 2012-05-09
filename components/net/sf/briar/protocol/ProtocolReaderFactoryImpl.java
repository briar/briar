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
import net.sf.briar.api.serial.StructReader;
import net.sf.briar.api.serial.ReaderFactory;

import com.google.inject.Inject;
import com.google.inject.Provider;

class ProtocolReaderFactoryImpl implements ProtocolReaderFactory {

	private final ReaderFactory readerFactory;
	private final Provider<StructReader<Ack>> ackProvider;
	private final Provider<StructReader<UnverifiedBatch>> batchProvider;
	private final Provider<StructReader<Offer>> offerProvider;
	private final Provider<StructReader<Request>> requestProvider;
	private final Provider<StructReader<SubscriptionUpdate>> subscriptionProvider;
	private final Provider<StructReader<TransportUpdate>> transportProvider;

	@Inject
	ProtocolReaderFactoryImpl(ReaderFactory readerFactory,
			Provider<StructReader<Ack>> ackProvider,
			Provider<StructReader<UnverifiedBatch>> batchProvider,
			Provider<StructReader<Offer>> offerProvider,
			Provider<StructReader<Request>> requestProvider,
			Provider<StructReader<SubscriptionUpdate>> subscriptionProvider,
			Provider<StructReader<TransportUpdate>> transportProvider) {
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
