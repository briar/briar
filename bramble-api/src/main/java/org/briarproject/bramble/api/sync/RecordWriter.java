package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.IOException;

@NotNullByDefault
public interface RecordWriter {

	void writeAck(Ack a) throws IOException;

	void writeMessage(byte[] raw) throws IOException;

	void writeOffer(Offer o) throws IOException;

	void writeRequest(Request r) throws IOException;

	void flush() throws IOException;
}
