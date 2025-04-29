package org.briarproject.briar.android.privategroup.conversation;

import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.threaded.ThreadItem;
import org.briarproject.briar.api.privategroup.GroupMessageHeader;

import javax.annotation.concurrent.NotThreadSafe;

import androidx.annotation.LayoutRes;
import androidx.annotation.UiThread;

@UiThread
@NotThreadSafe
public class GroupMessageItem extends ThreadItem {

	private final GroupId groupId;

	GroupMessageItem(GroupMessageHeader h) {
		super(h.getId(), h.getParentId(), h.getTimestamp(), h.getAuthor(),
				h.getAuthorInfo(), h.isRead());
		this.groupId = h.getGroupId();
	}

	public GroupId getGroupId() {
		return groupId;
	}

	@LayoutRes
	public int getLayout() {
		return R.layout.list_item_thread;
	}

}
