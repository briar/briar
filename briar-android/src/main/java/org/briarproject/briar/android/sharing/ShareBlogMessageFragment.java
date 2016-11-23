package org.briarproject.briar.android.sharing;

import android.os.Bundle;
import android.support.annotation.StringRes;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;

public class ShareBlogMessageFragment extends BaseMessageFragment {

	public final static String TAG = ShareBlogMessageFragment.class.getName();

	public static ShareBlogMessageFragment newInstance() {
		return new ShareBlogMessageFragment();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		setTitle(R.string.blogs_sharing_share);
		return super.onCreateView(inflater, container, savedInstanceState);
	}

	@Override
	@StringRes
	protected int getButtonText() {
		return R.string.blogs_sharing_button;
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
