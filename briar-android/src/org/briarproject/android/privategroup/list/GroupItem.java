package org.briarproject.android.privategroup.list;

import org.briarproject.api.identity.Author;
import org.briarproject.api.privategroup.GroupMessageHeader;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.sync.GroupId;
import org.jetbrains.annotations.NotNull;

// This class is not thread-safe
class GroupItem {

	private final PrivateGroup privateGroup;
	private int messageCount;
	private long lastUpdate;
	private int unreadCount;
	private boolean dissolved;

	GroupItem(@NotNull PrivateGroup privateGroup, int messageCount,
			long lastUpdate, int unreadCount, boolean dissolved) {

		this.privateGroup = privateGroup;
		this.messageCount = messageCount;
		this.lastUpdate = lastUpdate;
		this.unreadCount = unreadCount;
		this.dissolved = dissolved;
	}

	void addMessageHeader(GroupMessageHeader header) {
		messageCount++;
		if (header.getTimestamp() > lastUpdate) {
			lastUpdate = header.getTimestamp();
		}
		if (!header.isRead()) {
			unreadCount++;
		}
	}

	@NotNull
	PrivateGroup getPrivateGroup() {
		return privateGroup;
	}

	@NotNull
	GroupId getId() {
		return privateGroup.getId();
	}

	@NotNull
	Author getCreator() {
		return privateGroup.getAuthor();
	}

	@NotNull
	String getName() {
		return privateGroup.getName();
	}

	boolean isEmpty() {
		return messageCount == 0;
	}

	int getMessageCount() {
		return messageCount;
	}

	long getLastUpdate() {
		return lastUpdate;
	}

	int getUnreadCount() {
		return unreadCount;
	}

	boolean isDissolved() {
		return dissolved;
	}

}
