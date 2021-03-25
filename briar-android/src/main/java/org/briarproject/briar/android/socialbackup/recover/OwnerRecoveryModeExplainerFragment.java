package org.briarproject.briar.android.socialbackup;

import android.content.Context;
import android.os.Bundle;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class OwnerRecoveryModeExplainerFragment extends BaseFragment {

	protected ExplainerDismissedListener listener;
	public static final String TAG =
			OwnerRecoveryModeExplainerFragment.class.getName();

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requireActivity().setTitle(R.string.title_recovery_mode);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_recovery_owner_explainer,
				container, false);
		Button button = view.findViewById(R.id.beginButton);
		button.setOnClickListener(e -> listener.explainerDismissed());

		return view;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		listener = (ExplainerDismissedListener) context;
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
