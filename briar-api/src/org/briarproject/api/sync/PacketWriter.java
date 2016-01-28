package org.briarproject.api.sync;

import java.io.IOException;

public interface PacketWriter {

	int getMaxMessagesForAck(long capacity);

	int getMaxMessagesForRequest(long capacity);

	int getMaxMessagesForOffer(long capacity);

	void writeAck(Ack a) throws IOException;

	void writeMessage(byte[] raw) throws IOException;

	void writeOffer(Offer o) throws IOException;

	void writeRequest(Request r) throws IOException;

	void writeSubscriptionAck(SubscriptionAck a) throws IOException;

	void writeSubscriptionUpdate(SubscriptionUpdate u) throws IOException;

	void flush() throws IOException;
}
