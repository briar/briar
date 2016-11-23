package org.briarproject.briar.privategroup;

import static org.briarproject.briar.client.MessageTrackerConstants.MSG_KEY_READ;

interface GroupConstants {

	// Database keys
	String KEY_TYPE = "type";
	String KEY_TIMESTAMP = "timestamp";
	String KEY_READ = MSG_KEY_READ;
	String KEY_PARENT_MSG_ID = "parentMsgId";
	String KEY_PREVIOUS_MSG_ID = "previousMsgId";
	String KEY_MEMBER_ID = "memberId";
	String KEY_MEMBER_NAME = "memberName";
	String KEY_MEMBER_PUBLIC_KEY = "memberPublicKey";
	String KEY_INITIAL_JOIN_MSG = "initialJoinMsg";

	String GROUP_KEY_MEMBERS = "members";
	String GROUP_KEY_OUR_GROUP = "ourGroup";
	String GROUP_KEY_CREATOR_ID = "creatorId";
	String GROUP_KEY_DISSOLVED = "dissolved";
	String GROUP_KEY_VISIBILITY = "visibility";

}
