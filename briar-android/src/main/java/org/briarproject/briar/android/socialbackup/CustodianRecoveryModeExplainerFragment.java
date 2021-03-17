package org.briarproject.briar.android.socialbackup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.briar.android.fragment.BaseFragment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.briarproject.briar.R;

public class CustodianRecoveryModeExplainerFragment extends BaseFragment {

	public static final String TAG = CustodianRecoveryModeExplainerFragment.class.getName();

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requireActivity().setTitle(R.string.title_help_recover);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable
			ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_recovery_custodian_explainer,
				container, false);

		return view;
	}


	@Override
	public String getUniqueTag() {
		return TAG;
	}
}

