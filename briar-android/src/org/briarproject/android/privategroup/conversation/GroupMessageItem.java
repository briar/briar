package org.briarproject.android.privategroup.conversation;

import android.support.annotation.LayoutRes;
import android.support.annotation.UiThread;

import org.briarproject.R;
import org.briarproject.android.threaded.ThreadItem;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.Author.Status;
import org.briarproject.api.privategroup.GroupMessageHeader;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import javax.annotation.concurrent.NotThreadSafe;

@UiThread
@NotThreadSafe
class GroupMessageItem extends ThreadItem {

	private final GroupId groupId;

	private GroupMessageItem(MessageId messageId, GroupId groupId,
			MessageId parentId,
			String text, long timestamp, Author author, Status status,
			boolean isRead) {
		super(messageId, parentId, text, timestamp, author, status, isRead);
		this.groupId = groupId;
	}

	GroupMessageItem(GroupMessageHeader h, String text) {
		this(h.getId(), h.getGroupId(), h.getParentId(), text, h.getTimestamp(),
				h.getAuthor(), h.getAuthorStatus(), h.isRead());
	}

	public GroupId getGroupId() {
		return groupId;
	}

	@LayoutRes
	public int getLayout() {
		return R.layout.list_item_thread;
	}

}
