package org.briarproject.android.privategroup.creation;

import android.support.annotation.StringRes;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.sharing.BaseMessageFragment;

public class CreateGroupMessageFragment extends BaseMessageFragment {

	private final static String TAG =
			CreateGroupMessageFragment.class.getName();

	@Override
	@StringRes
	protected int getButtonText() {
		return R.string.groups_create_group_invitation_button;
	}

	@Override
	@StringRes
	protected int getHintText() {
		return R.string.forum_share_message;
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

}
