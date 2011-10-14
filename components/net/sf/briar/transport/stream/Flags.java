package net.sf.briar.transport.stream;

interface Flags {

	// Flags raised by the database listener
	static final int BATCH_RECEIVED = 1;
	static final int CONTACTS_UPDATED = 2;
	static final int MESSAGES_ADDED = 4;
	static final int SUBSCRIPTIONS_UPDATED = 8;
	static final int TRANSPORTS_UPDATED = 16;
	// Flags raised by the reading side of the connection
	static final int OFFER_RECEIVED = 32;
	static final int REQUEST_RECEIVED = 64;
}
