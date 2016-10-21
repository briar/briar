package org.briarproject.android.privategroup.conversation;

import org.briarproject.api.privategroup.GroupMessageHeader;

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

}
