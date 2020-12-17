package org.briarproject.briar.android.privategroup.list;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorInfo;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;
import org.briarproject.briar.api.privategroup.GroupMessageHeader;
import org.briarproject.briar.api.privategroup.PrivateGroup;

import androidx.annotation.Nullable;

// This class is not thread-safe
@NotNullByDefault
class GroupItem implements Comparable<GroupItem> {

	private final PrivateGroup privateGroup;
	private final AuthorInfo authorInfo;
	private int messageCount, unreadCount;
	private long timestamp;
	private boolean dissolved;

	GroupItem(PrivateGroup privateGroup, AuthorInfo authorInfo,
			GroupCount count, boolean dissolved) {
		this.privateGroup = privateGroup;
		this.authorInfo = authorInfo;
		this.messageCount = count.getMsgCount();
		this.unreadCount = count.getUnreadCount();
		this.timestamp = count.getLatestMsgTime();
		this.dissolved = dissolved;
	}

	GroupItem(GroupItem item) {
		this.privateGroup = item.privateGroup;
		this.authorInfo = item.authorInfo;
		this.messageCount = item.messageCount;
		this.unreadCount = item.unreadCount;
		this.timestamp = item.timestamp;
		this.dissolved = item.dissolved;
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

	GroupId getId() {
		return privateGroup.getId();
	}

	Author getCreator() {
		return privateGroup.getCreator();
	}

	AuthorInfo getCreatorInfo() {
		return authorInfo;
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

	@Override
	public boolean equals(@Nullable Object o) {
		return o instanceof GroupItem &&
				getId().equals(((GroupItem) o).getId());
	}

	@Override
	public int compareTo(GroupItem o) {
		if (this == o) return 0;
		// The group with the latest message comes first
		long aTime = getTimestamp(), bTime = o.getTimestamp();
		if (aTime > bTime) return -1;
		if (aTime < bTime) return 1;
		// Break ties by group name
		String aName = getName();
		String bName = o.getName();
		return String.CASE_INSENSITIVE_ORDER.compare(aName, bName);
	}
}
