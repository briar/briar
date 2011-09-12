package net.sf.briar.api.protocol;

/** User-defined type identifiers for encoding and decoding protocol objects. */
public interface Types {

	static final int ACK = 0;
	static final int AUTHOR = 1;
	static final int AUTHOR_ID = 2;
	static final int BATCH = 3;
	static final int BATCH_ID = 4;
	static final int GROUP = 5;
	static final int GROUP_ID = 6;
	static final int MESSAGE = 7;
	static final int MESSAGE_ID = 8;
	static final int OFFER = 9;
	static final int REQUEST = 10;
	static final int SUBSCRIPTION_UPDATE = 11;
	static final int TRANSPORT_PROPERTIES = 12;
	static final int TRANSPORT_UPDATE = 13;
}
