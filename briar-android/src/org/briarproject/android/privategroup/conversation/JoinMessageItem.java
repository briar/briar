package org.briarproject.android.privategroup.conversation;

import android.support.annotation.LayoutRes;
import android.support.annotation.UiThread;

import org.briarproject.R;
import org.briarproject.api.privategroup.JoinMessageHeader;
import org.briarproject.api.privategroup.Visibility;

import javax.annotation.concurrent.NotThreadSafe;

@UiThread
@NotThreadSafe
class JoinMessageItem extends GroupMessageItem {

	private Visibility visibility;
	private final boolean isInitial;

	JoinMessageItem(JoinMessageHeader h, String text) {
		super(h, text);
		this.visibility = h.getVisibility();
		this.isInitial = h.isInitial();
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
		return R.layout.list_item_group_join_notice;
	}

	public Visibility getVisibility() {
		return visibility;
	}

	public void setVisibility(Visibility visibility) {
		this.visibility = visibility;
	}

	public boolean isInitial() {
		return isInitial;
	}

}
