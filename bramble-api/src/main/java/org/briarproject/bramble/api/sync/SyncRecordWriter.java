package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.IOException;

@NotNullByDefault
public interface SyncRecordWriter {

	void writeAck(Ack a) throws IOException;

	void writeMessage(Message m) throws IOException;

	void writeOffer(Offer o) throws IOException;

	void writeRequest(Request r) throws IOException;

	void writeVersions(Versions v) throws IOException;

	void writePriority(Priority p) throws IOException;

	void flush() throws IOException;
}
