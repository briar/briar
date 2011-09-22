package net.sf.briar.api.protocol;

import java.io.IOException;

public interface ProtocolReader {

	boolean eof() throws IOException;

	boolean hasAck() throws IOException;
	Ack readAck() throws IOException;

	boolean hasBatch() throws IOException;
	Batch readBatch() throws IOException;

	boolean hasOffer() throws IOException;
	Offer readOffer() throws IOException;

	boolean hasRequest() throws IOException;
	Request readRequest() throws IOException;

	boolean hasSubscriptionUpdate() throws IOException;
	SubscriptionUpdate readSubscriptionUpdate() throws IOException;

	boolean hasTransportUpdate() throws IOException;
	TransportUpdate readTransportUpdate() throws IOException;
}
