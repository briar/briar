package org.briarproject.briar.api.remotewipe;

public interface RemoteWipeConstants {

	int THRESHOLD = 2;
    long MAX_MESSAGE_AGE = 24 * 60 * 60 * 1000;

	// Group metadata keys
	String GROUP_KEY_CONTACT_ID = "contactId";
	String GROUP_KEY_WIPERS = "wipers";
	String GROUP_KEY_RECEIVED_WIPE = "receivedWipe";

	// Message metadata keys
	String MSG_KEY_TIMESTAMP = "timestamp";
	String MSG_KEY_MESSAGE_TYPE = "messageType";
	String MSG_KEY_LOCAL = "local";
}
