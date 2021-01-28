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
public class BluetoothSetupStartFragment extends BaseFragment {
	private static final String TAG =
			BluetoothSetupStartFragment.class.getName();

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

		View v = inflater.inflate(R.layout.fragment_bluetooth_setup_start,
				container, false);

		// TODO device-BT and BT-plugin needs to be enabled at this point

		Button startButton = v.findViewById(R.id.startButton);
		startButton.setOnClickListener(view -> {
			showNextFragment(new BluetoothSetupChooseFragment());
		});
		startButton.setEnabled(true);

		return v;
	}

}
