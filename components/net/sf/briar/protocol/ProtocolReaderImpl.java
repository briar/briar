package net.sf.briar.protocol;

import java.io.IOException;
import java.io.InputStream;

import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.ProtocolReader;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;

class ProtocolReaderImpl implements ProtocolReader {

	private final Reader reader;

	ProtocolReaderImpl(InputStream in, ReaderFactory readerFactory,
			ObjectReader<Ack> ackReader, ObjectReader<Batch> batchReader,
			ObjectReader<Offer> offerReader,
			ObjectReader<Request> requestReader,
			ObjectReader<SubscriptionUpdate> subscriptionReader,
			ObjectReader<TransportUpdate> transportReader) {
		reader = readerFactory.createReader(in);
		reader.addObjectReader(Tags.ACK, ackReader);
		reader.addObjectReader(Tags.BATCH, batchReader);
		reader.addObjectReader(Tags.OFFER, offerReader);
		reader.addObjectReader(Tags.REQUEST, requestReader);
		reader.addObjectReader(Tags.SUBSCRIPTIONS, subscriptionReader);
		reader.addObjectReader(Tags.TRANSPORTS, transportReader);
	}

	public boolean hasAck() throws IOException {
		return reader.hasUserDefined(Tags.ACK);
	}

	public Ack readAck() throws IOException {
		return reader.readUserDefined(Tags.ACK, Ack.class);
	}

	public boolean hasBatch() throws IOException {
		return reader.hasUserDefined(Tags.BATCH);
	}

	public Batch readBatch() throws IOException {
		return reader.readUserDefined(Tags.BATCH, Batch.class);
	}

	public boolean hasOffer() throws IOException {
		return reader.hasUserDefined(Tags.OFFER);
	}

	public Offer readOffer() throws IOException {
		return reader.readUserDefined(Tags.OFFER, Offer.class);
	}

	public boolean hasRequest() throws IOException {
		return reader.hasUserDefined(Tags.REQUEST);
	}

	public Request readRequest() throws IOException {
		return reader.readUserDefined(Tags.REQUEST, Request.class);
	}

	public boolean hasSubscriptionUpdate() throws IOException {
		return reader.hasUserDefined(Tags.SUBSCRIPTIONS);
	}

	public SubscriptionUpdate readSubscriptionUpdate() throws IOException {
		return reader.readUserDefined(Tags.SUBSCRIPTIONS,
				SubscriptionUpdate.class);
	}

	public boolean hasTransportUpdate() throws IOException {
		return reader.hasUserDefined(Tags.TRANSPORTS);
	}

	public TransportUpdate readTransportUpdate() throws IOException {
		return reader.readUserDefined(Tags.TRANSPORTS, TransportUpdate.class);
	}
}
