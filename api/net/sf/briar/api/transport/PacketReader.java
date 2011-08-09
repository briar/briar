package net.sf.briar.api.transport;

import java.io.IOException;

import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportUpdate;

/**
 * Reads unencrypted packets from an underlying input stream and authenticates
 * them.
 */
public interface PacketReader {

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
