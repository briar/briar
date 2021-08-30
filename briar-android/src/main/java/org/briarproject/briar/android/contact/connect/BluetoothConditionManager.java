package org.briarproject.briar.android.contact.connect;

import android.app.Activity;
import android.content.Context;

import org.briarproject.briar.R;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.os.Build.VERSION.SDK_INT;
import static androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale;
import static org.briarproject.briar.android.util.UiUtils.getGoToSettingsListener;
import static org.briarproject.briar.android.util.UiUtils.isLocationEnabled;
import static org.briarproject.briar.android.util.UiUtils.showLocationDialog;

class BluetoothConditionManager {

	private enum Permission {
		UNKNOWN, GRANTED, SHOW_RATIONALE, PERMANENTLY_DENIED
	}

	private Permission locationPermission = Permission.UNKNOWN;

	/**
	 * Call this when the using activity or fragment starts,
	 * because permissions might have changed while it was stopped.
	 */
	void reset() {
		locationPermission = Permission.UNKNOWN;
	}

	@UiThread
	void onLocationPermissionResult(Activity activity,
			@Nullable Boolean result) {
		if (result != null && result) {
			locationPermission = Permission.GRANTED;
		} else if (shouldShowRequestPermissionRationale(activity,
				ACCESS_FINE_LOCATION)) {
			locationPermission = Permission.SHOW_RATIONALE;
		} else {
			locationPermission = Permission.PERMANENTLY_DENIED;
		}
	}

	boolean areRequirementsFulfilled(Context ctx,
			ActivityResultLauncher<String> permissionRequest,
			Runnable onLocationDenied) {
		boolean permissionGranted =
				SDK_INT < 23 || locationPermission == Permission.GRANTED;
		boolean locationEnabled = isLocationEnabled(ctx);
		if (permissionGranted && locationEnabled) return true;

		if (locationPermission == Permission.PERMANENTLY_DENIED) {
			showDenialDialog(ctx, onLocationDenied);
		} else if (locationPermission == Permission.SHOW_RATIONALE) {
			showRationale(ctx, permissionRequest);
		} else if (!locationEnabled) {
			showLocationDialog(ctx);
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
			ActivityResultLauncher<String> permissionRequest) {
		new AlertDialog.Builder(ctx, R.style.BriarDialogTheme)
				.setTitle(R.string.permission_location_title)
				.setMessage(R.string.permission_location_request_body)
				.setPositiveButton(R.string.ok, (dialog, which) ->
						permissionRequest.launch(ACCESS_FINE_LOCATION))
				.show();
	}

}
