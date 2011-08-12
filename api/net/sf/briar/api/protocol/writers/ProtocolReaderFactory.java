package net.sf.briar.api.protocol.writers;

import java.io.InputStream;

import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.serial.ObjectReader;

public interface ProtocolReaderFactory {

	ObjectReader<Ack> createAckReader(InputStream in);

	ObjectReader<Batch> createBatchReader(InputStream in);

	ObjectReader<Offer> createOfferReader(InputStream in);

	ObjectReader<Request> createRequestReader(InputStream in);

	ObjectReader<SubscriptionUpdate> createSubscriptionReader(InputStream in);

	ObjectReader<TransportUpdate> createTransportReader(InputStream in);
}
