package org.briarproject.briar.android.privategroup.conversation;

import android.support.annotation.LayoutRes;
import android.support.annotation.UiThread;

import org.briarproject.briar.R;
import org.briarproject.briar.api.privategroup.JoinMessageHeader;
import org.briarproject.briar.api.privategroup.Visibility;

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
	@LayoutRes
	public int getLayout() {
		return R.layout.list_item_group_join_notice;
	}

	Visibility getVisibility() {
		return visibility;
	}

	void setVisibility(Visibility visibility) {
		this.visibility = visibility;
	}

	boolean isInitial() {
		return isInitial;
	}

}
