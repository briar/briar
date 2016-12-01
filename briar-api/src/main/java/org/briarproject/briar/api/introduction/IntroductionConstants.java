package org.briarproject.briar.api.introduction;

import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;

public interface IntroductionConstants {

	/* Protocol roles */
	int ROLE_INTRODUCER = 0;
	int ROLE_INTRODUCEE = 1;

	/* Message types */
	int TYPE_REQUEST = 1;
	int TYPE_RESPONSE = 2;
	int TYPE_ACK = 3;
	int TYPE_ABORT = 4;

	/* Message Constants */
	String TYPE = "type";
	String GROUP_ID = "groupId";
	String SESSION_ID = "sessionId";
	String CONTACT = "contactId";
	String NAME = "name";
	String PUBLIC_KEY = "publicKey";
	String E_PUBLIC_KEY = "ephemeralPublicKey";
	String MSG = "msg";
	String ACCEPT = "accept";
	String TIME = "time";
	String TRANSPORT = "transport";
	String MESSAGE_ID = "messageId";
	String MESSAGE_TIME = "timestamp";
	String MAC = "mac";
	String SIGNATURE = "signature";

	/* Validation Constants */

	/**
	 * The length of the message authentication code in bytes.
	 */
	int MAC_LENGTH = 32;

	/**
	 * The maximum length of the introducer's optional message to the
	 * introducees in UTF-8 bytes.
	 */
	int MAX_INTRODUCTION_MESSAGE_LENGTH = MAX_MESSAGE_BODY_LENGTH - 1024;

	/* Introducer Local State Metadata */
	String STATE = "state";
	String ROLE = "role";
	String GROUP_ID_1 = "groupId1";
	String GROUP_ID_2 = "groupId2";
	String CONTACT_1 = "contact1";
	String CONTACT_2 = "contact2";
	String AUTHOR_ID_1 = "authorId1";
	String AUTHOR_ID_2 = "authorId2";
	String CONTACT_ID_1 = "contactId1";
	String CONTACT_ID_2 = "contactId2";
	String RESPONSE_1 = "response1";
	String RESPONSE_2 = "response2";

	/* Introduction Request Action */
	String PUBLIC_KEY1 = "publicKey1";
	String PUBLIC_KEY2 = "publicKey2";

	/* Introducee Local State Metadata (without those already defined) */
	String STORAGE_ID = "storageId";
	String INTRODUCER = "introducer";
	String LOCAL_AUTHOR_ID = "localAuthorId";
	String REMOTE_AUTHOR_ID = "remoteAuthorId";
	String OUR_PUBLIC_KEY = "ourEphemeralPublicKey";
	String OUR_PRIVATE_KEY = "ourEphemeralPrivateKey";
	String OUR_TIME = "ourTime";
	String ADDED_CONTACT_ID = "addedContactId";
	String NOT_OUR_RESPONSE = "notOurResponse";
	String EXISTS = "contactExists";
	String REMOTE_AUTHOR_IS_US = "remoteAuthorIsUs";
	String ANSWERED = "answered";
	String NONCE = "nonce";
	String MAC_KEY = "macKey";
	String OUR_TRANSPORT = "ourTransport";
	String OUR_MAC = "ourMac";
	String OUR_SIGNATURE = "ourSignature";

	String TASK = "task";
	int TASK_ADD_CONTACT = 0;
	int TASK_ACTIVATE_CONTACT = 1;
	int TASK_ABORT = 2;

}
