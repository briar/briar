package org.briarproject.api.sync;

import java.io.IOException;

public interface PacketReader {

	boolean eof() throws IOException;

	boolean hasAck() throws IOException;
	Ack readAck() throws IOException;

	boolean hasMessage() throws IOException;
	UnverifiedMessage readMessage() throws IOException;

	boolean hasOffer() throws IOException;
	Offer readOffer() throws IOException;

	boolean hasRequest() throws IOException;
	Request readRequest() throws IOException;

	boolean hasSubscriptionAck() throws IOException;
	SubscriptionAck readSubscriptionAck() throws IOException;

	boolean hasSubscriptionUpdate() throws IOException;
	SubscriptionUpdate readSubscriptionUpdate() throws IOException;

	boolean hasTransportAck() throws IOException;
	TransportAck readTransportAck() throws IOException;

	boolean hasTransportUpdate() throws IOException;
	TransportUpdate readTransportUpdate() throws IOException;
}
