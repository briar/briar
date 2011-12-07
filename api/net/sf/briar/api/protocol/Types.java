package net.sf.briar.api.protocol;

/** Struct identifiers for encoding and decoding protocol objects. */
public interface Types {

	static final int ACK = 0;
	static final int AUTHOR = 1;
	static final int BATCH = 2;
	static final int GROUP = 3;
	static final int MESSAGE = 4;
	static final int OFFER = 5;
	static final int REQUEST = 6;
	static final int SUBSCRIPTION_UPDATE = 7;
	static final int TRANSPORT = 8;
	static final int TRANSPORT_UPDATE = 9;
}
