package org.briarproject.briar.android.hotspot;

import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.provider.Settings;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.Context.WIFI_SERVICE;
import static android.os.Build.VERSION.SDK_INT;
import static androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale;
import static org.briarproject.briar.android.util.UiUtils.getGoToSettingsListener;

/**
 * This class ensures that the conditions to open a hotspot are fulfilled.
 * <p>
 * Be sure to call {@link #onRequestPermissionResult(Boolean)} and
 * {@link #onRequestWifiEnabledResult()} when you get the
 * {@link ActivityResult}.
 * <p>
 * As soon as {@link #checkAndRequestConditions()} returns true,
 * all conditions are fulfilled.
 */
@NotNullByDefault
class ConditionManager {

	private enum Permission {
		UNKNOWN, GRANTED, SHOW_RATIONALE, PERMANENTLY_DENIED
	}

	private Permission locationPermission = Permission.UNKNOWN;
	private Permission wifiSetting = Permission.SHOW_RATIONALE;

	private final FragmentActivity ctx;
	private final WifiManager wifiManager;
	private final ActivityResultLauncher<String> locationRequest;
	private final ActivityResultLauncher<Intent> wifiRequest;

	ConditionManager(FragmentActivity ctx,
			ActivityResultLauncher<String> locationRequest,
			ActivityResultLauncher<Intent> wifiRequest) {
		this.ctx = ctx;
		this.wifiManager = (WifiManager) ctx.getApplicationContext()
				.getSystemService(WIFI_SERVICE);
		this.locationRequest = locationRequest;
		this.wifiRequest = wifiRequest;
	}

	/**
	 * Call this to reset state when UI starts,
	 * because state might have changed.
	 */
	void resetPermissions() {
		locationPermission = Permission.UNKNOWN;
		wifiSetting = Permission.SHOW_RATIONALE;
	}

	/**
	 * This makes a request for location permission.
	 * If {@link #checkAndRequestConditions()} returns true, you can continue.
	 */
	void startConditionChecks() {
		locationRequest.launch(ACCESS_FINE_LOCATION);
	}

	/**
	 * @return true if conditions are fulfilled and flow can continue.
	 */
	boolean checkAndRequestConditions() {
		if (areEssentialPermissionsGranted()) return true;

		// If an essential permission has been permanently denied, ask the
		// user to change the setting
		if (locationPermission == Permission.PERMANENTLY_DENIED) {
			showDenialDialog(R.string.permission_location_title,
					R.string.permission_hotspot_location_denied_body,
					getGoToSettingsListener(ctx));
			return false;
		}
		if (wifiSetting == Permission.PERMANENTLY_DENIED) {
			showDenialDialog(R.string.wifi_settings_title,
					R.string.wifi_settings_request_denied_body,
					(d, w) -> requestEnableWiFi());
			return false;
		}

		// Should we show the rationale for location permission or Wi-Fi?
		if (locationPermission == Permission.SHOW_RATIONALE) {
			showRationale(R.string.permission_location_title,
					R.string.permission_hotspot_location_request_body,
					this::requestPermissions);
		} else if (wifiSetting == Permission.SHOW_RATIONALE) {
			showRationale(R.string.wifi_settings_title,
					R.string.wifi_settings_request_enable_body,
					this::requestEnableWiFi);
		}
		return false;
	}

	void onRequestPermissionResult(@Nullable Boolean granted) {
		if (granted != null && granted) {
			locationPermission = Permission.GRANTED;
		} else if (shouldShowRequestPermissionRationale(ctx,
				ACCESS_FINE_LOCATION)) {
			locationPermission = Permission.SHOW_RATIONALE;
		} else {
			locationPermission = Permission.PERMANENTLY_DENIED;
		}
	}

	void onRequestWifiEnabledResult() {
		wifiSetting = wifiManager.isWifiEnabled() ? Permission.GRANTED :
				Permission.PERMANENTLY_DENIED;
	}

	private boolean areEssentialPermissionsGranted() {
		if (SDK_INT < 29) {
			if (!wifiManager.isWifiEnabled()) {
				//noinspection deprecation
				return wifiManager.setWifiEnabled(true);
			}
			return true;
		} else {
			return locationPermission == Permission.GRANTED
					&& wifiManager.isWifiEnabled();
		}
	}

	private void showDenialDialog(@StringRes int title, @StringRes int body,
			OnClickListener onOkClicked) {
		AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
		builder.setTitle(title);
		builder.setMessage(body);
		builder.setPositiveButton(R.string.ok, onOkClicked);
		builder.setNegativeButton(R.string.cancel,
				(dialog, which) -> ctx.supportFinishAfterTransition());
		builder.show();
	}

	private void showRationale(@StringRes int title, @StringRes int body,
			Runnable onContinueClicked) {
		AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
		builder.setTitle(title);
		builder.setMessage(body);
		builder.setNeutralButton(R.string.continue_button,
				(dialog, which) -> onContinueClicked.run());
		builder.show();
	}

	private void requestPermissions() {
		locationRequest.launch(ACCESS_FINE_LOCATION);
	}

	private void requestEnableWiFi() {
		Intent i = SDK_INT < 29 ?
				new Intent(Settings.ACTION_WIFI_SETTINGS) :
				new Intent(Settings.Panel.ACTION_WIFI);
		wifiRequest.launch(i);
	}

}
