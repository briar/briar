package org.briarproject.briar.android.hotspot;

import android.content.Intent;
import android.provider.Settings;

import org.briarproject.briar.R;

import java.util.logging.Logger;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.util.Consumer;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale;
import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.briar.android.util.UiUtils.getGoToSettingsListener;
import static org.briarproject.briar.android.util.UiUtils.showDenialDialog;
import static org.briarproject.briar.android.util.UiUtils.showRationale;

/**
 * This class ensures that the conditions to open a hotspot are fulfilled on
 * API levels >= 29.
 * <p>
 * As soon as {@link #checkAndRequestConditions()} returns true,
 * all conditions are fulfilled.
 */
@RequiresApi(29)
class ConditionManager29Impl extends ConditionManager {

	private static final Logger LOG =
			getLogger(ConditionManager29Impl.class.getName());

	private Permission locationPermission = Permission.UNKNOWN;

	private final ActivityResultLauncher<String> locationRequest;
	private final ActivityResultLauncher<Intent> wifiRequest;

	ConditionManager29Impl(ActivityResultCaller arc,
			Consumer<Boolean> permissionUpdateCallback) {
		super(permissionUpdateCallback);
		locationRequest = arc.registerForActivityResult(
				new RequestPermission(), granted -> {
					onRequestPermissionResult(granted);
					permissionUpdateCallback.accept(true);
				});
		wifiRequest = arc.registerForActivityResult(
				new StartActivityForResult(),
				result -> permissionUpdateCallback.accept(true));
	}

	@Override
	void onStart() {
		locationPermission = Permission.UNKNOWN;
	}

	private boolean areEssentialPermissionsGranted() {
		if (LOG.isLoggable(INFO)) {
			LOG.info(String.format("areEssentialPermissionsGranted(): " +
							"locationPermission? %s, " +
							"wifiManager.isWifiEnabled()? %b",
					locationPermission,
					wifiManager.isWifiEnabled()));
		}
		return locationPermission == Permission.GRANTED &&
				wifiManager.isWifiEnabled();
	}

	@Override
	boolean checkAndRequestConditions() {
		if (areEssentialPermissionsGranted()) return true;

		if (locationPermission == Permission.UNKNOWN) {
			locationRequest.launch(ACCESS_FINE_LOCATION);
			return false;
		}

		// If the location permission has been permanently denied, ask the
		// user to change the setting
		if (locationPermission == Permission.PERMANENTLY_DENIED) {
			showDenialDialog(ctx, R.string.permission_location_title,
					R.string.permission_hotspot_location_denied_body,
					getGoToSettingsListener(ctx),
					() -> permissionUpdateCallback.accept(false));
			return false;
		}

		// Should we show the rationale for location permission?
		if (locationPermission == Permission.SHOW_RATIONALE) {
			showRationale(ctx, R.string.permission_location_title,
					R.string.permission_hotspot_location_request_body,
					this::requestPermissions,
					() -> permissionUpdateCallback.accept(false));
			return false;
		}

		// If Wifi is not enabled, we show the rationale for enabling Wifi?
		if (!wifiManager.isWifiEnabled()) {
			showRationale(ctx, R.string.wifi_settings_title,
					R.string.wifi_settings_request_enable_body,
					this::requestEnableWiFi,
					() -> permissionUpdateCallback.accept(false));
			return false;
		}

		// we shouldn't usually reach this point, but if we do, return false
		// anyway to force a recheck. Maybe some condition changed in the
		// meantime.
		return false;
	}

	private void onRequestPermissionResult(@Nullable Boolean granted) {
		if (granted != null && granted) {
			locationPermission = Permission.GRANTED;
		} else if (shouldShowRequestPermissionRationale(ctx,
				ACCESS_FINE_LOCATION)) {
			locationPermission = Permission.SHOW_RATIONALE;
		} else {
			locationPermission = Permission.PERMANENTLY_DENIED;
		}
	}

	private void requestPermissions() {
		locationRequest.launch(ACCESS_FINE_LOCATION);
	}

	private void requestEnableWiFi() {
		wifiRequest.launch(new Intent(Settings.Panel.ACTION_WIFI));
	}

}
