package org.briarproject.briar.android.contact.add.nearby;

import android.content.Context;

import org.briarproject.briar.R;

import java.util.Map;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Consumer;
import androidx.fragment.app.FragmentActivity;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.CAMERA;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale;
import static androidx.core.content.ContextCompat.checkSelfPermission;
import static org.briarproject.briar.android.util.UiUtils.getGoToSettingsListener;
import static org.briarproject.briar.android.util.UiUtils.isLocationEnabled;
import static org.briarproject.briar.android.util.UiUtils.showLocationDialog;

class AddNearbyContactPermissionManager {

	private enum Permission {
		UNKNOWN, GRANTED, SHOW_RATIONALE, PERMANENTLY_DENIED
	}

	private Permission cameraPermission = Permission.UNKNOWN;
	private Permission locationPermission = Permission.UNKNOWN;

	private final FragmentActivity ctx;
	private final Consumer<String[]> requestPermissions;
	private final boolean isBluetoothSupported;

	AddNearbyContactPermissionManager(FragmentActivity ctx,
			Consumer<String[]> requestPermissions,
			boolean isBluetoothSupported) {
		this.ctx = ctx;
		this.requestPermissions = requestPermissions;
		this.isBluetoothSupported = isBluetoothSupported;
	}

	void resetPermissions() {
		cameraPermission = Permission.UNKNOWN;
		locationPermission = Permission.UNKNOWN;
	}

	static boolean areEssentialPermissionsGranted(Context ctx,
			boolean isBluetoothSupported) {
		int ok = PERMISSION_GRANTED;
		return checkSelfPermission(ctx, CAMERA) == ok &&
				(SDK_INT < 23 ||
						checkSelfPermission(ctx, ACCESS_FINE_LOCATION) == ok ||
						!isBluetoothSupported);
	}

	private boolean areEssentialPermissionsGranted() {
		return cameraPermission == Permission.GRANTED &&
				(SDK_INT < 23 || locationPermission == Permission.GRANTED ||
						!isBluetoothSupported);
	}

	boolean checkPermissions() {
		boolean locationEnabled = isLocationEnabled(ctx);
		if (locationEnabled && areEssentialPermissionsGranted()) return true;
		// If an essential permission has been permanently denied, ask the
		// user to change the setting
		if (cameraPermission == Permission.PERMANENTLY_DENIED) {
			showDenialDialog(R.string.permission_camera_title,
					R.string.permission_camera_denied_body);
			return false;
		}
		if (isBluetoothSupported &&
				locationPermission == Permission.PERMANENTLY_DENIED) {
			showDenialDialog(R.string.permission_location_title,
					R.string.permission_location_denied_body);
			return false;
		}
		// Should we show the rationale for one or both permissions?
		if (cameraPermission == Permission.SHOW_RATIONALE &&
				locationPermission == Permission.SHOW_RATIONALE) {
			showRationale(R.string.permission_camera_location_title,
					R.string.permission_camera_location_request_body);
		} else if (cameraPermission == Permission.SHOW_RATIONALE) {
			showRationale(R.string.permission_camera_title,
					R.string.permission_camera_request_body);
		} else if (locationPermission == Permission.SHOW_RATIONALE) {
			showRationale(R.string.permission_location_title,
					R.string.permission_location_request_body);
		} else if (locationEnabled) {
			requestPermissions();
		} else {
			showLocationDialog(ctx);
		}
		return false;
	}

	private void showDenialDialog(@StringRes int title, @StringRes int body) {
		AlertDialog.Builder builder =
				new AlertDialog.Builder(ctx, R.style.BriarDialogTheme);
		builder.setTitle(title);
		builder.setMessage(body);
		builder.setPositiveButton(R.string.ok, getGoToSettingsListener(ctx));
		builder.setNegativeButton(R.string.cancel,
				(dialog, which) -> ctx.supportFinishAfterTransition());
		builder.show();
	}

	private void showRationale(@StringRes int title, @StringRes int body) {
		AlertDialog.Builder builder =
				new AlertDialog.Builder(ctx, R.style.BriarDialogTheme);
		builder.setTitle(title);
		builder.setMessage(body);
		builder.setNeutralButton(R.string.continue_button,
				(dialog, which) -> requestPermissions());
		builder.show();
	}

	private void requestPermissions() {
		String[] permissions;
		if (isBluetoothSupported) {
			permissions = new String[] {CAMERA, ACCESS_FINE_LOCATION};
		} else {
			permissions = new String[] {CAMERA};
		}
		requestPermissions.accept(permissions);
	}

	void onRequestPermissionResult(Map<String, Boolean> result) {
		if (gotPermission(CAMERA, result)) {
			cameraPermission = Permission.GRANTED;
		} else if (shouldShowRationale(CAMERA)) {
			cameraPermission = Permission.SHOW_RATIONALE;
		} else {
			cameraPermission = Permission.PERMANENTLY_DENIED;
		}
		if (isBluetoothSupported) {
			if (gotPermission(ACCESS_FINE_LOCATION, result)) {
				locationPermission = Permission.GRANTED;
			} else if (shouldShowRationale(ACCESS_FINE_LOCATION)) {
				locationPermission = Permission.SHOW_RATIONALE;
			} else {
				locationPermission = Permission.PERMANENTLY_DENIED;
			}
		}
	}

	private boolean gotPermission(String permission,
			Map<String, Boolean> result) {
		Boolean permissionResult = result.get(permission);
		return permissionResult == null ?
				isGranted(permission) : permissionResult;
	}

	private boolean isGranted(String permission) {
		return checkSelfPermission(ctx, permission) == PERMISSION_GRANTED;
	}

	private boolean shouldShowRationale(String permission) {
		return shouldShowRequestPermissionRationale(ctx, permission);
	}

}
