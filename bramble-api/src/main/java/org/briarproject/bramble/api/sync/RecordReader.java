package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.IOException;

@NotNullByDefault
public interface RecordReader {

	boolean eof() throws IOException;

	boolean hasAck() throws IOException;

	Ack readAck() throws IOException;

	boolean hasMessage() throws IOException;

	Message readMessage() throws IOException;

	boolean hasOffer() throws IOException;

	Offer readOffer() throws IOException;

	boolean hasRequest() throws IOException;

	Request readRequest() throws IOException;

}
