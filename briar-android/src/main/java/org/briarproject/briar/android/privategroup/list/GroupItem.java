package org.briarproject.briar.android.privategroup.list;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;
import org.briarproject.briar.api.privategroup.GroupMessageHeader;
import org.briarproject.briar.api.privategroup.PrivateGroup;

// This class is not thread-safe
@NotNullByDefault
class GroupItem {

	private final PrivateGroup privateGroup;
	private int messageCount, unreadCount;
	private long timestamp;
	private boolean dissolved;

	GroupItem(PrivateGroup privateGroup, GroupCount count, boolean dissolved) {
		this.privateGroup = privateGroup;
		this.messageCount = count.getMsgCount();
		this.unreadCount = count.getUnreadCount();
		this.timestamp = count.getLatestMsgTime();
		this.dissolved = dissolved;
	}

	void addMessageHeader(GroupMessageHeader header) {
		messageCount++;
		if (header.getTimestamp() > timestamp) {
			timestamp = header.getTimestamp();
		}
		if (!header.isRead()) {
			unreadCount++;
		}
	}

	PrivateGroup getPrivateGroup() {
		return privateGroup;
	}

	GroupId getId() {
		return privateGroup.getId();
	}

	Author getCreator() {
		return privateGroup.getCreator();
	}

	String getName() {
		return privateGroup.getName();
	}

	boolean isEmpty() {
		return messageCount == 0;
	}

	int getMessageCount() {
		return messageCount;
	}

	long getTimestamp() {
		return timestamp;
	}

	int getUnreadCount() {
		return unreadCount;
	}

	boolean isDissolved() {
		return dissolved;
	}

	void setDissolved() {
		dissolved = true;
	}

}
