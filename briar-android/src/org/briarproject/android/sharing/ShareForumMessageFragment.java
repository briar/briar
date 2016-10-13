package org.briarproject.android.sharing;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;

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
	protected int getButtonText() {
		return R.string.forum_share_button;
	}

	@Override
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
