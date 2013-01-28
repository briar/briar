package net.sf.briar.api.protocol;

/** Struct identifiers for encoding and decoding protocol objects. */
public interface Types {

	int AUTHOR = 0;
	int GROUP = 1;
	int ACK = 2;
	int EXPIRY_ACK = 3;
	int EXPIRY_UPDATE = 4;
	int MESSAGE = 5;
	int OFFER = 6;
	int REQUEST = 7;
	int SUBSCRIPTION_ACK = 8;
	int SUBSCRIPTION_UPDATE = 9;
	int TRANSPORT_ACK = 10;
	int TRANSPORT_UPDATE = 11;
}
