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

	boolean hasRetentionAck() throws IOException;
	org.briarproject.api.sync.RetentionAck readRetentionAck() throws IOException;

	boolean hasRetentionUpdate() throws IOException;
	org.briarproject.api.sync.RetentionUpdate readRetentionUpdate() throws IOException;

	boolean hasSubscriptionAck() throws IOException;
	org.briarproject.api.sync.SubscriptionAck readSubscriptionAck() throws IOException;

	boolean hasSubscriptionUpdate() throws IOException;
	org.briarproject.api.sync.SubscriptionUpdate readSubscriptionUpdate() throws IOException;

	boolean hasTransportAck() throws IOException;
	TransportAck readTransportAck() throws IOException;

	boolean hasTransportUpdate() throws IOException;
	org.briarproject.api.sync.TransportUpdate readTransportUpdate() throws IOException;
}
