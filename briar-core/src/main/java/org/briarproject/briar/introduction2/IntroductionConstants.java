package org.briarproject.briar.introduction2;

interface IntroductionConstants {

	// Message metadata keys
	String MSG_KEY_MESSAGE_TYPE = "messageType";
	String MSG_KEY_SESSION_ID = "sessionId";
	String MSG_KEY_TIMESTAMP = "timestamp";
	String MSG_KEY_LOCAL = "local";
	String MSG_KEY_VISIBLE_IN_UI = "visibleInUi";
	String MSG_KEY_AVAILABLE_TO_ANSWER = "availableToAnswer";
	String MSG_KEY_INVITATION_ACCEPTED = "invitationAccepted";

	// Session Keys
	String SESSION_KEY_SESSION_ID = "sessionId";
	String SESSION_KEY_ROLE = "role";
	String SESSION_KEY_STATE = "state";
	String SESSION_KEY_REQUEST_TIMESTAMP = "requestTimestamp";
	String SESSION_KEY_LOCAL_TIMESTAMP = "localTimestamp";
	String SESSION_KEY_LAST_LOCAL_MESSAGE_ID = "lastLocalMessageId";
	String SESSION_KEY_LAST_REMOTE_MESSAGE_ID = "lastRemoteMessageId";

	// Session Keys Introducer
	String SESSION_KEY_INTRODUCEE_1 = "introducee1";
	String SESSION_KEY_INTRODUCEE_2 = "introducee2";
	String SESSION_KEY_GROUP_ID = "groupId";
	String SESSION_KEY_AUTHOR = "author";

	// Session Keys Introducee
	String SESSION_KEY_INTRODUCER = "introducer";
	String SESSION_KEY_EPHEMERAL_PUBLIC_KEY = "ephemeralPublicKey";
	String SESSION_KEY_EPHEMERAL_PRIVATE_KEY = "ephemeralPrivateKey";
	String SESSION_KEY_TRANSPORT_PROPERTIES = "transportProperties";
	String SESSION_KEY_ACCEPT_TIMESTAMP = "acceptTimestamp";
	String SESSION_KEY_MASTER_KEY = "masterKey";
	String SESSION_KEY_REMOTE_AUTHOR = "remoteAuthor";
	String SESSION_KEY_REMOTE_EPHEMERAL_PUBLIC_KEY = "remoteEphemeralPublicKey";
	String SESSION_KEY_REMOTE_TRANSPORT_PROPERTIES =
			"remoteTransportProperties";
	String SESSION_KEY_REMOTE_ACCEPT_TIMESTAMP = "remoteAcceptTimestamp";
	String SESSION_KEY_TRANSPORT_KEYS = "transportKeys";

}
