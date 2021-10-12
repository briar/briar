package org.briarproject.briar.android.remotewipe;

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

public class RemoteWipeSetupExplainerFragment extends
		BaseFragment {

		public static final String TAG =
				RemoteWipeSetupExplainerFragment.class.getName();

		@Inject
		ViewModelProvider.Factory viewModelFactory;

		private RemoteWipeSetupViewModel viewModel;

		@Override
		public void injectFragment(ActivityComponent component) {
			component.inject(this);
			viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
					.get(RemoteWipeSetupViewModel.class);
		}

		@Nullable
		@Override
		public View onCreateView(@NonNull LayoutInflater inflater,
				@Nullable ViewGroup container,
				@Nullable Bundle savedInstanceState) {
			View view = inflater.inflate(R.layout.fragment_remote_wipe_setup_explainer,
					container, false);

			Button confirmButton = view.findViewById(R.id.button_confirm);
			confirmButton.setOnClickListener(e -> viewModel.onExplainerConfirmed());

			Button cancelButton = view.findViewById(R.id.button_cancel);
			cancelButton.setOnClickListener(e -> viewModel.onExplainerCancelled());
			return view;
		}

		@Override
		public String getUniqueTag() {
			return TAG;
		}
}
