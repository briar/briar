package org.briarproject.briar.android.contact.add.nearby;

import android.content.Context;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.BaseActivity;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.CAMERA;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale;
import static androidx.core.content.ContextCompat.checkSelfPermission;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_PERMISSION_CAMERA_LOCATION;
import static org.briarproject.briar.android.util.UiUtils.getGoToSettingsListener;

class AddNearbyContactPermissionManager {

	private enum Permission {
		UNKNOWN, GRANTED, SHOW_RATIONALE, PERMANENTLY_DENIED
	}

	private Permission cameraPermission = Permission.UNKNOWN;
	private Permission locationPermission = Permission.UNKNOWN;

	private final BaseActivity ctx;
	private final boolean isBluetoothSupported;

	AddNearbyContactPermissionManager(BaseActivity ctx,
			boolean isBluetoothSupported) {
		this.ctx = ctx;
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

	boolean areEssentialPermissionsGranted() {
		return cameraPermission == Permission.GRANTED &&
				(SDK_INT < 23 || locationPermission == Permission.GRANTED ||
						!isBluetoothSupported);
	}

	boolean checkPermissions() {
		if (areEssentialPermissionsGranted()) return true;
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
		} else {
			requestPermissions();
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
		ActivityCompat.requestPermissions(ctx, permissions,
				REQUEST_PERMISSION_CAMERA_LOCATION);
	}

	void onRequestPermissionsResult(int requestCode, String[] permissions,
			int[] grantResults, Runnable onPermissionsGranted) {
		if (requestCode != REQUEST_PERMISSION_CAMERA_LOCATION)
			throw new AssertionError();
		if (gotPermission(CAMERA, permissions, grantResults)) {
			cameraPermission = Permission.GRANTED;
		} else if (shouldShowRationale(CAMERA)) {
			cameraPermission = Permission.SHOW_RATIONALE;
		} else {
			cameraPermission = Permission.PERMANENTLY_DENIED;
		}
		if (isBluetoothSupported) {
			if (gotPermission(ACCESS_FINE_LOCATION, permissions,
					grantResults)) {
				locationPermission = Permission.GRANTED;
			} else if (shouldShowRationale(ACCESS_FINE_LOCATION)) {
				locationPermission = Permission.SHOW_RATIONALE;
			} else {
				locationPermission = Permission.PERMANENTLY_DENIED;
			}
		}
		// If a permission dialog has been shown, showing the QR code fragment
		// on this call path would cause a crash due to
		// https://code.google.com/p/android/issues/detail?id=190966.
		// In that case the isResumed flag prevents the fragment from being
		// shown here, and showQrCodeFragmentIfAllowed() will be called again
		// from onPostResume().
		if (checkPermissions()) onPermissionsGranted.run();
	}

	private boolean gotPermission(String permission, String[] permissions,
			int[] grantResults) {
		for (int i = 0; i < permissions.length; i++) {
			if (permission.equals(permissions[i]))
				return grantResults[i] == PERMISSION_GRANTED;
		}
		return false;
	}

	private boolean shouldShowRationale(String permission) {
		return shouldShowRequestPermissionRationale(ctx, permission);
	}

}
