package org.briarproject.briar.android.contact.add.nearby;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions;
import androidx.lifecycle.ViewModelProvider;

import static android.view.View.FOCUS_DOWN;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class AddNearbyContactIntroFragment extends BaseFragment {

	public static final String TAG =
			AddNearbyContactIntroFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private AddNearbyContactViewModel viewModel;
	private AddNearbyContactPermissionManager permissionManager;

	private ScrollView scrollView;

	private final ActivityResultLauncher<String[]> permissionLauncher =
			registerForActivityResult(new RequestMultiplePermissions(), r -> {
				permissionManager.onRequestPermissionResult(r);
				if (permissionManager.checkPermissions()) {
					viewModel.showQrCodeFragmentIfAllowed();
				}
			});

	public static AddNearbyContactIntroFragment newInstance() {
		Bundle args = new Bundle();
		AddNearbyContactIntroFragment
				fragment = new AddNearbyContactIntroFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(AddNearbyContactViewModel.class);
		permissionManager = new AddNearbyContactPermissionManager(
				requireActivity(), permissionLauncher::launch,
				viewModel.isBluetoothSupported());
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {

		View v = inflater.inflate(R.layout.fragment_keyagreement_id, container,
				false);
		scrollView = v.findViewById(R.id.scrollView);
		View button = v.findViewById(R.id.continueButton);
		button.setOnClickListener(view -> viewModel.onContinueClicked(() ->
				permissionManager.checkPermissions()
		));
		return v;
	}

	@Override
	public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		// We don't do this in AddNearbyContactFragment#onDestroy()
		// because it gets called when creating AddNearbyContactFragment
		// in landscape orientation to force portrait orientation.
		viewModel.stopListening();
	}

	@Override
	public void onStart() {
		super.onStart();
		// Permissions may have been granted manually while we were stopped
		permissionManager.resetPermissions();
		scrollView.post(() -> scrollView.fullScroll(FOCUS_DOWN));
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

}
