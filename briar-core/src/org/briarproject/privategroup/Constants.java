package org.briarproject.privategroup;

import static org.briarproject.clients.BdfConstants.MSG_KEY_READ;

interface Constants {

	// Database keys
	String KEY_TYPE = "type";
	String KEY_TIMESTAMP = "timestamp";
	String KEY_READ = MSG_KEY_READ;
	String KEY_PARENT_MSG_ID = "parentMsgId";
	String KEY_NEW_MEMBER_MSG_ID = "newMemberMsgId";
	String KEY_PREVIOUS_MSG_ID = "previousMsgId";
	String KEY_MEMBER_ID = "memberId";
	String KEY_MEMBER_NAME = "memberName";
	String KEY_MEMBER_PUBLIC_KEY = "memberPublicKey";

	String KEY_MEMBERS = "members";
	String KEY_DISSOLVED = "dissolved";

}
