package org.briarproject.briar.android.conversation;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.contact.ContactItem;
import org.briarproject.briar.android.util.RequestBluetoothDiscoverable;

import javax.inject.Inject;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.DialogInterface.BUTTON_POSITIVE;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.Objects.requireNonNull;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class BluetoothConnecterDialogFragment extends DialogFragment {

	final static String TAG = BluetoothConnecterDialogFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private ConversationViewModel viewModel;
	private BluetoothConnecter bluetoothConnecter;

	private final ActivityResultLauncher<Integer> bluetoothDiscoverableRequest =
			registerForActivityResult(new RequestBluetoothDiscoverable(),
					this::onBluetoothDiscoverable);
	private final ActivityResultLauncher<String> permissionRequest =
			registerForActivityResult(new RequestPermission(),
					this::onPermissionRequestResult);

	@Override
	public void onAttach(Context ctx) {
		super.onAttach(ctx);
		((BaseActivity) requireActivity()).getActivityComponent().inject(this);
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(ConversationViewModel.class);
		bluetoothConnecter = viewModel.getBluetoothConnecter();
	}

	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		Context ctx = requireContext();
		return new AlertDialog.Builder(ctx, R.style.BriarDialogTheme)
				.setTitle(R.string.dialog_title_connect_via_bluetooth)
				.setMessage(R.string.dialog_message_connect_via_bluetooth)
				// actual listener gets set in onResume()
				.setPositiveButton(R.string.start, null)
				.setNegativeButton(R.string.cancel, null)
				.setCancelable(false) // keep it open until dismissed
				.create();
	}

	@Override
	public void onStart() {
		super.onStart();
		bluetoothConnecter.reset();
		if (bluetoothConnecter.isBluetoothNotSupported()) {
			showToast(R.string.toast_connect_via_bluetooth_error);
			dismiss();
			return;
		}
		// MenuItem only gets enabled after contactItem has loaded
		ContactItem contact =
				requireNonNull(viewModel.getContactItem().getValue());
		ContactId contactId = contact.getContact().getId();
		if (bluetoothConnecter.isConnectedViaBluetooth(contactId)) {
			showToast(R.string.toast_connect_via_bluetooth_success);
			dismiss();
			return;
		}
		if (bluetoothConnecter.isDiscovering()) {
			showToast(R.string.toast_connect_via_bluetooth_already_discovering);
			dismiss();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		// Set the click listener for the START button here
		// to prevent it from automatically dismissing the dialog.
		// The dialog is shown in onStart(), so we set the listener here later.
		AlertDialog dialog = (AlertDialog) getDialog();
		Button positiveButton = dialog.getButton(BUTTON_POSITIVE);
		positiveButton.setOnClickListener(this::onStartClicked);
	}

	private void onStartClicked(View v) {
		// The dialog starts a permission request which comes back as true
		// if the permission is already granted.
		// So we can use the request as a generic entry point to the whole flow.
		permissionRequest.launch(ACCESS_FINE_LOCATION);
	}

	private void onPermissionRequestResult(@Nullable Boolean result) {
		Activity a = requireActivity();
		// update permission result in BluetoothConnecter
		bluetoothConnecter.onLocationPermissionResult(a, result);
		// what to do when the user denies granting the location permission
		Runnable onLocationPermissionDenied = () -> {
			Toast.makeText(requireContext(),
					R.string.toast_connect_via_bluetooth_no_location_permission,
					LENGTH_LONG).show();
			dismiss();
		};
		// if requirements are fulfilled, request Bluetooth discoverability
		if (bluetoothConnecter.areRequirementsFulfilled(a, permissionRequest,
				onLocationPermissionDenied)) {
			bluetoothDiscoverableRequest.launch(120); // for 2min
		}
	}

	private void onBluetoothDiscoverable(@Nullable Boolean result) {
		if (result != null && result) {
			// MenuItem only gets enabled after contactItem has loaded
			ContactItem contact =
					requireNonNull(viewModel.getContactItem().getValue());
			bluetoothConnecter.onBluetoothDiscoverable(contact);
			dismiss();
		} else {
			showToast(R.string.toast_connect_via_bluetooth_not_discoverable);
		}
	}

	private void showToast(@StringRes int stringRes) {
		Toast.makeText(requireContext(), stringRes, LENGTH_LONG).show();
	}

}
