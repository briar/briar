package org.briarproject.briar.android.login;

import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.widget.Button;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.api.android.LockManager;

import java.util.logging.Logger;

import javax.inject.Inject;

import static android.os.Build.VERSION.SDK_INT;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_KEYGUARD_UNLOCK;
import static org.briarproject.briar.android.util.UiUtils.hasScreenLock;

@RequiresApi(21)
@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class UnlockActivity extends BaseActivity {

	private static final Logger LOG =
			Logger.getLogger(UnlockActivity.class.getName());

	@Inject
	LockManager lockManager;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);
		overridePendingTransition(0, 0);
		setContentView(R.layout.activity_unlock);

		Button button = findViewById(R.id.unlock);
		button.setOnClickListener(view -> requestKeyguardUnlock());
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_KEYGUARD_UNLOCK) {
			if (resultCode == RESULT_OK) unlock();
			else finish();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		// Show keyguard after onActivityResult() as been called.
		// Check if app is still locked, lockable
		// and not finishing (which is possible if recreated)
		if (lockManager.isLocked() && hasScreenLock(this) && !isFinishing()) {
			requestKeyguardUnlock();
		}
	}

	@Override
	public void onBackPressed() {
		moveTaskToBack(true);
	}

	private void requestKeyguardUnlock() {
		KeyguardManager keyguardManager =
				(KeyguardManager) getSystemService(KEYGUARD_SERVICE);
		if (keyguardManager == null) throw new AssertionError();
		Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(
				SDK_INT < 23 ? getString(R.string.lock_unlock_verbose) :
						getString(R.string.lock_unlock), null);
		if (intent == null) {
			// the user must have removed the screen lock since locked
			LOG.warning("Unlocking without keyguard");
			unlock();
		} else {
			startActivityForResult(intent, REQUEST_KEYGUARD_UNLOCK);
			overridePendingTransition(0, 0);
		}
	}

	private void unlock() {
		lockManager.setLocked(false);
		setResult(RESULT_OK);
		finish();
		overridePendingTransition(0, 0);
	}

}
