package org.briarproject.briar.android.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;

import javax.annotation.Nullable;

public class SignOutFragment extends BaseFragment {

	private static final String TAG = SignOutFragment.class.getName();

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_sign_out, container, false);
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		// no need to inject
	}

}
