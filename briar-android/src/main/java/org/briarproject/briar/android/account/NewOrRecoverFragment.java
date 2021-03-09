package org.briarproject.briar.android.account;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class NewOrRecoverFragment extends BaseFragment {

	public static final String TAG = NewOrRecoverFragment.class.getName();

	public static NewOrRecoverFragment newInstance() {
		Bundle bundle = new Bundle();
		NewOrRecoverFragment fragment = new NewOrRecoverFragment();
		fragment.setArguments(bundle);
		return fragment;
	}
	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requireActivity().setTitle(R.string.setup_title);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable
			ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_new_or_recover,
				container, false);
		return view;
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
