package org.briarproject.briar.android.socialbackup.recover;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.socialbackup.ScanQrButtonListener;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

public class OwnerRecoveryModeMainFragment extends BaseFragment {

	public static final String NUM_RECOVERED = "num_recovered";

	public static final String TAG =
			OwnerRecoveryModeMainFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private OwnerReturnShardViewModel viewModel;

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(OwnerReturnShardViewModel.class);
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

//	@Override
//	public void onCreate(@Nullable Bundle savedInstanceState) {
//		super.onCreate(savedInstanceState);
//		requireActivity().setTitle(R.string.title_recovery_mode);
//
//		Bundle args = requireArguments();
//	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_recovery_owner_main,
				container, false);

		TextView textViewCount = view.findViewById(R.id.textViewShardCount);
		textViewCount.setText(String.format("%d", viewModel.getNumberOfShards()));

		Button button = view.findViewById(R.id.button);
		button.setOnClickListener(e -> viewModel.onContinueClicked());
		return view;
	}
}
