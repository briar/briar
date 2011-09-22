package net.sf.briar.protocol;

import java.io.IOException;
import java.io.InputStream;

import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.ProtocolReader;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.Types;
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
		reader.addObjectReader(Types.ACK, ackReader);
		reader.addObjectReader(Types.BATCH, batchReader);
		reader.addObjectReader(Types.OFFER, offerReader);
		reader.addObjectReader(Types.REQUEST, requestReader);
		reader.addObjectReader(Types.SUBSCRIPTION_UPDATE, subscriptionReader);
		reader.addObjectReader(Types.TRANSPORT_UPDATE, transportReader);
	}

	public boolean eof() throws IOException {
		return reader.eof();
	}

	public boolean hasAck() throws IOException {
		return reader.hasUserDefined(Types.ACK);
	}

	public Ack readAck() throws IOException {
		return reader.readUserDefined(Types.ACK, Ack.class);
	}

	public boolean hasBatch() throws IOException {
		return reader.hasUserDefined(Types.BATCH);
	}

	public Batch readBatch() throws IOException {
		return reader.readUserDefined(Types.BATCH, Batch.class);
	}

	public boolean hasOffer() throws IOException {
		return reader.hasUserDefined(Types.OFFER);
	}

	public Offer readOffer() throws IOException {
		return reader.readUserDefined(Types.OFFER, Offer.class);
	}

	public boolean hasRequest() throws IOException {
		return reader.hasUserDefined(Types.REQUEST);
	}

	public Request readRequest() throws IOException {
		return reader.readUserDefined(Types.REQUEST, Request.class);
	}

	public boolean hasSubscriptionUpdate() throws IOException {
		return reader.hasUserDefined(Types.SUBSCRIPTION_UPDATE);
	}

	public SubscriptionUpdate readSubscriptionUpdate() throws IOException {
		return reader.readUserDefined(Types.SUBSCRIPTION_UPDATE,
				SubscriptionUpdate.class);
	}

	public boolean hasTransportUpdate() throws IOException {
		return reader.hasUserDefined(Types.TRANSPORT_UPDATE);
	}

	public TransportUpdate readTransportUpdate() throws IOException {
		return reader.readUserDefined(Types.TRANSPORT_UPDATE, TransportUpdate.class);
	}
}
