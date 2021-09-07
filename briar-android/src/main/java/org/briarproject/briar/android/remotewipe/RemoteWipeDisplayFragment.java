package org.briarproject.briar.android.remotewipe;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;

import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

public class RemoteWipeDisplayFragment extends
		BaseFragment {

	public static final String TAG = RemoteWipeDisplayFragment.class.getName();

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
		View view = inflater.inflate(R.layout.fragment_display_remote_wipe,
				container, false);
        List<String> wiperNames = viewModel.getWiperNames();
		StringBuilder custodianNamesString = new StringBuilder();
		for (String custodianName : wiperNames) {
			custodianNamesString
					.append("• ")
					.append(custodianName)
					.append("\n");
		}
		TextView textViewThreshold = view.findViewById(R.id.textViewWipers);
		textViewThreshold.setText(custodianNamesString.toString());

		Button button = view.findViewById(R.id.button);
		button.setOnClickListener(e -> viewModel.onModifyWipers());
		return view;
	}
	@Override
	public String getUniqueTag() {
		return TAG;
	}
}
