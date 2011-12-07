package net.sf.briar.api.protocol;

/** Struct identifiers for encoding and decoding protocol objects. */
public interface Types {

	// FIXME: Batch ID, message ID don't need to be structs
	static final int ACK = 0;
	static final int AUTHOR = 1;
	static final int BATCH = 2;
	static final int BATCH_ID = 3;
	static final int GROUP = 4;
	static final int MESSAGE = 5;
	static final int MESSAGE_ID = 6;
	static final int OFFER = 7;
	static final int REQUEST = 8;
	static final int SUBSCRIPTION_UPDATE = 9;
	static final int TRANSPORT = 10;
	static final int TRANSPORT_UPDATE = 11;
}
