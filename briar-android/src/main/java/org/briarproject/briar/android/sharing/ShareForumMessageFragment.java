package org.briarproject.briar.android.sharing;

import android.os.Bundle;
import android.support.annotation.StringRes;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;

public class ShareForumMessageFragment extends BaseMessageFragment {

	public final static String TAG = ShareForumMessageFragment.class.getName();

	public static ShareForumMessageFragment newInstance() {
		return new ShareForumMessageFragment();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		setTitle(R.string.forum_share_button);
		return super.onCreateView(inflater, container, savedInstanceState);
	}

	@Override
	@StringRes
	protected int getButtonText() {
		return R.string.forum_share_button;
	}

	@Override
	@StringRes
	protected int getHintText() {
		return R.string.forum_share_message;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

}
