package org.briarproject.api.sync;

import java.io.IOException;

public interface PacketReader {

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
