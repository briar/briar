package org.briarproject.briar.android.bluetoothsetup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.lifecycle.ViewModelProvider;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class BluetoothSetupChooseFragment extends BaseFragment {
	private static final String TAG =
			BluetoothSetupChooseFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private BluetoothSetupViewModel viewModel;

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		if (getActivity() == null || getContext() == null) return null;

		viewModel = new ViewModelProvider(requireActivity())
				.get(BluetoothSetupViewModel.class);

		View v =
				inflater.inflate(R.layout.fragment_bluetooth_setup_choose,
						container, false);

		// TODO to enable when user picks a device from list

		Button continueButton = v.findViewById(R.id.continueButton);
		continueButton.setOnClickListener(view -> {
			showNextFragment(new BluetoothSetupPendingFragment());
		});
		continueButton.setEnabled(true);

//		RecyclerView devices = v.findViewById(R.id.devices);
//		devices.setHasFixedSize(true);
//		final LinearLayoutManager layoutManager =
//				new LinearLayoutManager(getActivity(),
//						LinearLayoutManager.VERTICAL, false);
//		devices.setLayoutManager(layoutManager);

		return v;
	}
}
