package net.sf.briar.api.protocol.writers;

import java.io.OutputStream;

public interface ProtocolWriterFactory {

	AckWriter createAckWriter(OutputStream out);

	BatchWriter createBatchWriter(OutputStream out);

	OfferWriter createOfferWriter(OutputStream out);

	RequestWriter createRequestWriter(OutputStream out);

	SubscriptionWriter createSubscriptionWriter(OutputStream out);

	TransportWriter createTransportWriter(OutputStream out);
}
