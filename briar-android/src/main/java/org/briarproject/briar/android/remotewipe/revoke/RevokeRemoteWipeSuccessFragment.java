package org.briarproject.briar.android.remotewipe.revoke;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

public class RevokeRemoteWipeSuccessFragment extends BaseFragment {

	public static final String TAG =
			RevokeRemoteWipeSuccessFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private RevokeRemoteWipeViewModel viewModel;

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(RevokeRemoteWipeViewModel.class);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_revoke_remote_wipe_success,
				container, false);

		Button button = view.findViewById(R.id.button);
		button.setOnClickListener(e -> viewModel.onSuccessDismissed());
		return view;
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}
}
