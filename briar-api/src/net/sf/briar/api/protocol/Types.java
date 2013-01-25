package net.sf.briar.api.protocol;

/** Struct identifiers for encoding and decoding protocol objects. */
public interface Types {

	int ACK = 0;
	int AUTHOR = 1;
	int GROUP = 3;
	int MESSAGE = 4;
	int OFFER = 5;
	int REQUEST = 6;
	int SUBSCRIPTION_ACK = 7;
	int SUBSCRIPTION_UPDATE = 8;
	int TRANSPORT_ACK = 9;
	int TRANSPORT_UPDATE = 10;
}
