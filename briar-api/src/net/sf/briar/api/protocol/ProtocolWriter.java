package net.sf.briar.api.protocol;

import java.io.IOException;

public interface ProtocolWriter {

	int getMaxMessagesForAck(long capacity);

	int getMaxMessagesForOffer(long capacity);

	void writeAck(Ack a) throws IOException;

	void writeMessage(byte[] raw) throws IOException;

	void writeOffer(Offer o) throws IOException;

	void writeRequest(Request r) throws IOException;

	void writeSubscriptionUpdate(SubscriptionUpdate s) throws IOException;

	void writeTransportUpdate(TransportUpdate t) throws IOException;

	void flush() throws IOException;

	void close() throws IOException;
}
