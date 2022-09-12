package org.briarproject.briar.android.contact.connect;

import android.app.Activity;
import android.content.Context;

import org.briarproject.briar.R;
import org.briarproject.briar.android.util.Permission;
import org.briarproject.briar.android.util.UiUtils;

import java.util.Map;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale;
import static androidx.core.content.ContextCompat.checkSelfPermission;
import static org.briarproject.briar.android.util.Permission.GRANTED;
import static org.briarproject.briar.android.util.Permission.PERMANENTLY_DENIED;
import static org.briarproject.briar.android.util.Permission.SHOW_RATIONALE;
import static org.briarproject.briar.android.util.Permission.UNKNOWN;
import static org.briarproject.briar.android.util.UiUtils.getGoToSettingsListener;
import static org.briarproject.briar.android.util.UiUtils.isLocationEnabled;
import static org.briarproject.briar.android.util.UiUtils.requestBluetoothPermissions;
import static org.briarproject.briar.android.util.UiUtils.showLocationDialog;
import static org.briarproject.briar.android.util.UiUtils.wasGrantedBluetoothPermissions;

class BluetoothConditionManager {

	private Permission locationPermission = SDK_INT < 31 ? UNKNOWN : GRANTED;
	private Permission bluetoothPermissions = SDK_INT < 31 ? GRANTED : UNKNOWN;

	/**
	 * Call this when the using activity or fragment starts,
	 * because permissions might have changed while it was stopped.
	 */
	void reset() {
		locationPermission = SDK_INT < 31 ? UNKNOWN : GRANTED;
		bluetoothPermissions = SDK_INT < 31 ? GRANTED : UNKNOWN;
	}

	@UiThread
	void requestPermissions(ActivityResultLauncher<String[]> launcher) {
		if (SDK_INT < 31) {
			launcher.launch(new String[] {ACCESS_FINE_LOCATION});
		} else {
			requestBluetoothPermissions(launcher);
		}
	}

	@UiThread
	void onLocationPermissionResult(Activity activity,
			@Nullable Map<String, Boolean> result) {
		if (SDK_INT < 31) {
			if (gotPermission(activity, result)) {
				locationPermission = GRANTED;
			} else if (shouldShowRequestPermissionRationale(activity,
					ACCESS_FINE_LOCATION)) {
				locationPermission = SHOW_RATIONALE;
			} else {
				locationPermission = PERMANENTLY_DENIED;
			}
		} else {
			if (wasGrantedBluetoothPermissions(result)) {
				bluetoothPermissions = GRANTED;
			} else if (shouldShowRequestPermissionRationale(activity,
					BLUETOOTH_CONNECT)) {
				bluetoothPermissions = SHOW_RATIONALE;
			} else {
				bluetoothPermissions = PERMANENTLY_DENIED;
			}
		}
	}

	boolean areRequirementsFulfilled(FragmentActivity ctx,
			ActivityResultLauncher<String[]> permissionRequest,
			Runnable onLocationDenied) {
		boolean permissionGranted =
				(SDK_INT < 23 || locationPermission == GRANTED) &&
						bluetoothPermissions == GRANTED;
		boolean locationEnabled = isLocationEnabled(ctx);
		if (permissionGranted && locationEnabled) return true;

		if (locationPermission == PERMANENTLY_DENIED) {
			showDenialDialog(ctx, onLocationDenied);
		} else if (locationPermission == SHOW_RATIONALE) {
			showRationale(ctx, permissionRequest);
		} else if (!locationEnabled) {
			showLocationDialog(ctx);
		} else if (bluetoothPermissions == PERMANENTLY_DENIED) {
			UiUtils.showDenialDialog(ctx, R.string.permission_bluetooth_title,
					R.string.permission_bluetooth_denied_body);
		} else if (bluetoothPermissions == SHOW_RATIONALE && SDK_INT >= 31) {
			UiUtils.showRationale(ctx, R.string.permission_bluetooth_title,
					R.string.permission_bluetooth_body, () ->
							requestBluetoothPermissions(permissionRequest));
		}
		return false;
	}

	private void showDenialDialog(Context ctx, Runnable onLocationDenied) {
		new AlertDialog.Builder(ctx, R.style.BriarDialogTheme)
				.setTitle(R.string.permission_location_title)
				.setMessage(R.string.permission_location_denied_body)
				.setPositiveButton(R.string.ok, getGoToSettingsListener(ctx))
				.setNegativeButton(R.string.cancel, (v, d) ->
						onLocationDenied.run())
				.show();
	}

	private void showRationale(Context ctx,
			ActivityResultLauncher<String[]> permissionRequest) {
		new AlertDialog.Builder(ctx, R.style.BriarDialogTheme)
				.setTitle(R.string.permission_location_title)
				.setMessage(R.string.permission_location_request_body)
				.setPositiveButton(R.string.ok, (dialog, which) ->
						permissionRequest.launch(
								new String[] {ACCESS_FINE_LOCATION}))
				.show();
	}

	private boolean gotPermission(Context ctx,
			@Nullable Map<String, Boolean> result) {
		Boolean permissionResult =
				result == null ? null : result.get(ACCESS_FINE_LOCATION);
		return permissionResult == null ? isLocationPermissionGranted(ctx) :
				permissionResult;
	}

	private boolean isLocationPermissionGranted(Context ctx) {
		return checkSelfPermission(ctx, ACCESS_FINE_LOCATION) ==
				PERMISSION_GRANTED;
	}

}
