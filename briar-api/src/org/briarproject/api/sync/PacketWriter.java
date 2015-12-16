package org.briarproject.api.sync;

import java.io.IOException;

public interface PacketWriter {

	int getMaxMessagesForAck(long capacity);

	int getMaxMessagesForRequest(long capacity);

	int getMaxMessagesForOffer(long capacity);

	void writeAck(Ack a) throws IOException;

	void writeMessage(byte[] raw) throws IOException;

	void writeOffer(org.briarproject.api.sync.Offer o) throws IOException;

	void writeRequest(Request r) throws IOException;

	void writeRetentionAck(org.briarproject.api.sync.RetentionAck a) throws IOException;

	void writeRetentionUpdate(org.briarproject.api.sync.RetentionUpdate u) throws IOException;

	void writeSubscriptionAck(org.briarproject.api.sync.SubscriptionAck a) throws IOException;

	void writeSubscriptionUpdate(org.briarproject.api.sync.SubscriptionUpdate u) throws IOException;

	void writeTransportAck(org.briarproject.api.sync.TransportAck a) throws IOException;

	void writeTransportUpdate(org.briarproject.api.sync.TransportUpdate u) throws IOException;

	void flush() throws IOException;
}
