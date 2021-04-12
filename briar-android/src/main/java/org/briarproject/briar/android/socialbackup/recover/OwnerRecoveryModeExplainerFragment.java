package org.briarproject.briar.android.socialbackup.recover;

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

public class OwnerRecoveryModeExplainerFragment extends BaseFragment {

	public static final String TAG =
			OwnerRecoveryModeExplainerFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private OwnerReturnShardViewModel viewModel;

//	@Override
//	public void onCreate(@Nullable Bundle savedInstanceState) {
//		super.onCreate(savedInstanceState);
//		requireActivity().setTitle(R.string.title_recovery_mode);
//	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(OwnerReturnShardViewModel.class);
	}

	@Override
	public String getUniqueTag() { return TAG; }

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_recovery_owner_explainer,
				container, false);
		Button button = view.findViewById(R.id.beginButton);
		button.setOnClickListener(e -> viewModel.onContinueClicked());

		return view;
	}

}
