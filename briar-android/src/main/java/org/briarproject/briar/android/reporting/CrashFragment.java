package org.briarproject.briar.android.reporting;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.AndroidComponent;
import org.briarproject.briar.android.BriarApplication;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class CrashFragment extends Fragment {

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private ReportViewModel viewModel;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		FragmentActivity a = requireActivity();
		BriarApplication app =
				(BriarApplication) a.getApplicationContext();
		AndroidComponent androidComponent = app.getApplicationComponent();
		androidComponent.inject(this);
		viewModel = new ViewModelProvider(a, viewModelFactory)
				.get(ReportViewModel.class);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View v = inflater
				.inflate(R.layout.fragment_crash, container, false);

		v.findViewById(R.id.acceptButton).setOnClickListener(view ->
				viewModel.showReport());
		v.findViewById(R.id.declineButton).setOnClickListener(view ->
				viewModel.closeReport());

		return v;
	}

}
