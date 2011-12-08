package net.sf.briar.api.protocol;

import java.io.IOException;

public interface ProtocolWriter {

	void flush() throws IOException;

	int getMaxBatchesForAck(long capacity);

	int getMaxMessagesForOffer(long capacity);

	int getMessageCapacityForBatch(long capacity);

	void writeAck(Ack a) throws IOException;

	void writeBatch(RawBatch b) throws IOException;

	void writeOffer(Offer o) throws IOException;

	void writeRequest(Request r) throws IOException;

	void writeSubscriptionUpdate(SubscriptionUpdate s) throws IOException;

	void writeTransportUpdate(TransportUpdate t) throws IOException;
}
