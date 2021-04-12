package org.briarproject.briar.android.socialbackup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.socialbackup.recover.CustodianReturnShardViewModel;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

public class CustodianRecoveryModeExplainerFragment extends BaseFragment {

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private CustodianReturnShardViewModel viewModel;

	public static final String TAG =
			CustodianRecoveryModeExplainerFragment.class.getName();

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(CustodianReturnShardViewModel.class);
	}
//	@Override
//	public void onCreate(@Nullable Bundle savedInstanceState) {
//		super.onCreate(savedInstanceState);
//		requireActivity().setTitle(R.string.title_help_recover);
//	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable
			ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view =
				inflater.inflate(R.layout.fragment_recovery_custodian_explainer,
						container, false);

		Button button = view.findViewById(R.id.button);
		button.setOnClickListener(e -> viewModel.onContinueClicked());
		return view;
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}
}

