package org.briarproject.android.privategroup.conversation;

import android.support.annotation.LayoutRes;
import android.support.annotation.UiThread;

import org.briarproject.R;
import org.briarproject.api.privategroup.GroupMessageHeader;

import javax.annotation.concurrent.NotThreadSafe;

@UiThread
@NotThreadSafe
class JoinMessageItem extends GroupMessageItem {

	JoinMessageItem(GroupMessageHeader h,
			String text) {
		super(h, text);
	}

	@Override
	public int getLevel() {
		return 0;
	}

	@Override
	public boolean hasDescendants() {
		return false;
	}

	@LayoutRes
	public int getLayout() {
		return R.layout.list_item_thread_notice;
	}

}
