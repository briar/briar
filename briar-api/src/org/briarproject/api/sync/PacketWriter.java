package org.briarproject.api.sync;

import java.io.IOException;

public interface PacketWriter {

	void writeAck(Ack a) throws IOException;

	void writeMessage(byte[] raw) throws IOException;

	void writeOffer(Offer o) throws IOException;

	void writeRequest(Request r) throws IOException;

	void flush() throws IOException;
}
