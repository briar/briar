package net.sf.briar.api.messaging;

/** Struct identifiers for encoding and decoding protocol objects. */
public interface Types {

	int AUTHOR = 0;
	int GROUP = 1;
	int ACK = 2;
	int MESSAGE = 3;
	int OFFER = 4;
	int REQUEST = 5;
	int RETENTION_ACK = 6;
	int RETENTION_UPDATE = 7;
	int SUBSCRIPTION_ACK = 8;
	int SUBSCRIPTION_UPDATE = 9;
	int TRANSPORT_ACK = 10;
	int TRANSPORT_UPDATE = 11;
}
