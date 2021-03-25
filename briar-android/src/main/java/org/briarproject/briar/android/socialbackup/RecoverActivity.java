package org.briarproject.briar.android.socialbackup;

import android.os.Bundle;
import android.widget.Toast;

import org.briarproject.bramble.api.keyagreement.KeyAgreementResult;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.fragment.BaseFragment;

import javax.annotation.Nullable;

import androidx.annotation.UiThread;
import androidx.core.app.ActivityCompat;

import static android.Manifest.permission.CAMERA;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_PERMISSION_CAMERA_LOCATION;

public class RecoverActivity extends BaseActivity implements
		BaseFragment.BaseFragmentListener, ExplainerDismissedListener,
		ScanQrButtonListener, ShardQrCodeFragment.ShardQrCodeEventListener {

	@Override
	public void keyAgreementFailed() {

	}

	@Nullable
	@Override
	public String keyAgreementWaiting() {
		return null;
	}

	@Nullable
	@Override
	public String keyAgreementStarted() {
		return null;
	}

	@Override
	public void keyAgreementAborted(boolean remoteAborted) {

	}

	@Nullable
	@Override
	public String keyAgreementFinished(KeyAgreementResult result) {
		return null;
	}

	private enum Permission {
		UNKNOWN, GRANTED, SHOW_RATIONALE, PERMANENTLY_DENIED
	}

	private Permission cameraPermission = Permission.UNKNOWN;


	private int numRecovered;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_recover);

		numRecovered = 0; // TODO - retrieve this from somewhere

		// only show the explainer if we have no shards
		if (numRecovered == 0) {
			OwnerRecoveryModeExplainerFragment fragment =
					new OwnerRecoveryModeExplainerFragment();
			showInitialFragment(fragment);
		} else {
			OwnerRecoveryModeMainFragment fragment =
					OwnerRecoveryModeMainFragment.newInstance(numRecovered);
			showInitialFragment(fragment);
		}
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void explainerDismissed() {
		OwnerRecoveryModeMainFragment fragment =
				OwnerRecoveryModeMainFragment.newInstance(numRecovered);
		showNextFragment(fragment);
	}

	@Override
	public void scanQrButtonClicked() {
		if (checkPermissions()) showQrCodeFragment();
	}


	private void showQrCodeFragment() {
		ShardQrCodeFragment f = ShardQrCodeFragment.newInstance();
		showNextFragment(f);
	}

	private void requestPermissions() {
		String[] permissions = new String[] {CAMERA};
		ActivityCompat.requestPermissions(this, permissions,
				REQUEST_PERMISSION_CAMERA_LOCATION);
	}

	@Override
	@UiThread
	public void onRequestPermissionsResult(int requestCode,
			String[] permissions, int[] grantResults) {
		if (requestCode != REQUEST_PERMISSION_CAMERA_LOCATION)
			throw new AssertionError();
		if (gotPermission(CAMERA, permissions, grantResults)) {
			cameraPermission = Permission.GRANTED;
		} else if (shouldShowRationale(CAMERA)) {
			cameraPermission = Permission.SHOW_RATIONALE;
		} else {
			cameraPermission = Permission.PERMANENTLY_DENIED;
		}
		// If a permission dialog has been shown, showing the QR code fragment
		// on this call path would cause a crash due to
		// https://code.google.com/p/android/issues/detail?id=190966.
		// In that case the isResumed flag prevents the fragment from being
		// shown here, and showQrCodeFragmentIfAllowed() will be called again
		// from onPostResume().
		if (checkPermissions()) showQrCodeFragment();
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
		return ActivityCompat.shouldShowRequestPermissionRationale(this,
				permission);
	}

	private boolean checkPermissions() {
		if (areEssentialPermissionsGranted()) return true;
		// If an essential permission has been permanently denied, ask the
		// user to change the setting
		if (cameraPermission == Permission.PERMANENTLY_DENIED) {
			Toast.makeText(this,
					"camera permission is denied",
					Toast.LENGTH_SHORT).show();
//			showDenialDialog(R.string.permission_camera_title,
//					R.string.permission_camera_denied_body);
			return false;
		}
		if (cameraPermission == Permission.SHOW_RATIONALE) {
//			showRationale(R.string.permission_camera_title,
//					R.string.permission_camera_request_body);
			Toast.makeText(this,
					"camera permission - show rationale",
					Toast.LENGTH_SHORT).show();
		} else {
			requestPermissions();
		}
		return false;
	}

	@Override
	@Deprecated
	public void runOnDbThread(Runnable runnable) {
		throw new RuntimeException("Don't use this deprecated method here.");
	}

	private boolean areEssentialPermissionsGranted() {
		return cameraPermission == Permission.GRANTED;
	}
}
