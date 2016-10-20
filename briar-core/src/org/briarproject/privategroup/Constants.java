package org.briarproject.privategroup;

import static org.briarproject.clients.BdfConstants.MSG_KEY_READ;

interface Constants {

	// Database keys
	String KEY_TYPE = "type";
	String KEY_TIMESTAMP = "timestamp";
	String KEY_READ = MSG_KEY_READ;
	String KEY_AUTHOR_NAME = "authorName";
	String KEY_AUTHOR_PUBLIC_KEY = "authorPublicKey";

	// Messaging Group Metadata
	String KEY_PREVIOUS_MSG_ID = "previousMsgId";
}
