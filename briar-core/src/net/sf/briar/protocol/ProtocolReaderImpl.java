package net.sf.briar.protocol;

import static net.sf.briar.api.protocol.Types.ACK;
import static net.sf.briar.api.protocol.Types.MESSAGE;
import static net.sf.briar.api.protocol.Types.OFFER;
import static net.sf.briar.api.protocol.Types.REQUEST;
import static net.sf.briar.api.protocol.Types.SUBSCRIPTION_ACK;
import static net.sf.briar.api.protocol.Types.SUBSCRIPTION_UPDATE;
import static net.sf.briar.api.protocol.Types.TRANSPORT_ACK;
import static net.sf.briar.api.protocol.Types.TRANSPORT_UPDATE;

import java.io.IOException;
import java.io.InputStream;

import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.ProtocolReader;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionAck;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportAck;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.protocol.UnverifiedMessage;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.StructReader;

class ProtocolReaderImpl implements ProtocolReader {

	private final Reader reader;

	ProtocolReaderImpl(InputStream in, ReaderFactory readerFactory,
			StructReader<Ack> ackReader,
			StructReader<UnverifiedMessage> messageReader,
			StructReader<Offer> offerReader,
			StructReader<Request> requestReader,
			StructReader<SubscriptionAck> subscriptionAckReader,
			StructReader<SubscriptionUpdate> subscriptionUpdateReader,
			StructReader<TransportAck> transportAckReader,
			StructReader<TransportUpdate> transportUpdateReader) {
		reader = readerFactory.createReader(in);
		reader.addStructReader(ACK, ackReader);
		reader.addStructReader(MESSAGE, messageReader);
		reader.addStructReader(OFFER, offerReader);
		reader.addStructReader(REQUEST, requestReader);
		reader.addStructReader(SUBSCRIPTION_ACK, subscriptionAckReader);
		reader.addStructReader(SUBSCRIPTION_UPDATE, subscriptionUpdateReader);
		reader.addStructReader(TRANSPORT_ACK, transportAckReader);
		reader.addStructReader(TRANSPORT_UPDATE, transportUpdateReader);
	}

	public boolean eof() throws IOException {
		return reader.eof();
	}

	public boolean hasAck() throws IOException {
		return reader.hasStruct(ACK);
	}

	public Ack readAck() throws IOException {
		return reader.readStruct(ACK, Ack.class);
	}

	public boolean hasMessage() throws IOException {
		return reader.hasStruct(MESSAGE);
	}

	public UnverifiedMessage readMessage() throws IOException {
		return reader.readStruct(MESSAGE, UnverifiedMessage.class);
	}

	public boolean hasOffer() throws IOException {
		return reader.hasStruct(OFFER);
	}

	public Offer readOffer() throws IOException {
		return reader.readStruct(OFFER, Offer.class);
	}

	public boolean hasRequest() throws IOException {
		return reader.hasStruct(REQUEST);
	}

	public Request readRequest() throws IOException {
		return reader.readStruct(REQUEST, Request.class);
	}

	public boolean hasSubscriptionAck() throws IOException {
		return reader.hasStruct(SUBSCRIPTION_ACK);
	}

	public SubscriptionAck readSubscriptionAck() throws IOException {
		return reader.readStruct(SUBSCRIPTION_ACK, SubscriptionAck.class);
	}

	public boolean hasSubscriptionUpdate() throws IOException {
		return reader.hasStruct(SUBSCRIPTION_UPDATE);
	}

	public SubscriptionUpdate readSubscriptionUpdate() throws IOException {
		return reader.readStruct(SUBSCRIPTION_UPDATE,
				SubscriptionUpdate.class);
	}

	public boolean hasTransportAck() throws IOException {
		return reader.hasStruct(TRANSPORT_ACK);
	}

	public TransportAck readTransportAck() throws IOException {
		return reader.readStruct(TRANSPORT_ACK, TransportAck.class);
	}

	public boolean hasTransportUpdate() throws IOException {
		return reader.hasStruct(TRANSPORT_UPDATE);
	}

	public TransportUpdate readTransportUpdate() throws IOException {
		return reader.readStruct(TRANSPORT_UPDATE, TransportUpdate.class);
	}
}
