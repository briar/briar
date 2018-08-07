package org.briarproject.briar.android.login;

import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.widget.Button;
import android.widget.Toast;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.api.android.LockManager;

import java.util.logging.Logger;

import javax.inject.Inject;

import moe.feng.support.biometricprompt.BiometricPromptCompat;
import moe.feng.support.biometricprompt.BiometricPromptCompat.Builder;
import moe.feng.support.biometricprompt.BiometricPromptCompat.IAuthenticationCallback;
import moe.feng.support.biometricprompt.BiometricPromptCompat.IAuthenticationResult;

import static android.os.Build.VERSION.SDK_INT;
import static moe.feng.support.biometricprompt.BiometricPromptCompat.BIOMETRIC_ERROR_CANCELED;
import static moe.feng.support.biometricprompt.BiometricPromptCompat.BIOMETRIC_ERROR_LOCKOUT;
import static moe.feng.support.biometricprompt.BiometricPromptCompat.BIOMETRIC_ERROR_LOCKOUT_PERMANENT;
import static moe.feng.support.biometricprompt.BiometricPromptCompat.BIOMETRIC_ERROR_USER_CANCELED;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_KEYGUARD_UNLOCK;
import static org.briarproject.briar.android.util.UiUtils.hasKeyguardLock;
import static org.briarproject.briar.android.util.UiUtils.hasUsableFingerprint;

@RequiresApi(21)
@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class UnlockActivity extends BaseActivity {

	private static final Logger LOG =
			Logger.getLogger(UnlockActivity.class.getName());
	private static final String KEYGUARD_SHOWN = "keyguardShown";

	@Inject
	LockManager lockManager;

	private boolean keyguardShown = false;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);
		overridePendingTransition(0, 0);
		setContentView(R.layout.activity_unlock);

		Button button = findViewById(R.id.unlock);
		button.setOnClickListener(view -> requestUnlock());

		keyguardShown = state != null && state.getBoolean(KEYGUARD_SHOWN);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		// Saving whether we've shown the keyguard already is necessary
		// for Android 6 when this activity gets destroyed.
		//
		// This will not help Android 5.
		// There the system will show the keyguard once again.
		// So if this activity was destroyed, the user needs to enter PIN twice.
		outState.putBoolean(KEYGUARD_SHOWN, keyguardShown);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_KEYGUARD_UNLOCK) {
			if (resultCode == RESULT_OK) unlock();
			else {
				finish();
				overridePendingTransition(0, 0);
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		// Show keyguard after onActivityResult() as been called.
		// Check if app is still locked, lockable
		// and not finishing (which is possible if recreated)
		if (!keyguardShown && lockManager.isLocked() && !isFinishing()) {
			requestUnlock();
		} else if (!lockManager.isLocked()) {
			setResult(RESULT_OK);
			finish();
		}
	}

	private void requestUnlock() {
		if (hasUsableFingerprint(this)) {
			requestFingerprintUnlock();
		} else {
			requestKeyguardUnlock();
		}
	}

	@Override
	public void onBackPressed() {
		moveTaskToBack(true);
	}

	private void requestFingerprintUnlock() {
		BiometricPromptCompat biometricPrompt = new Builder(this)
				.setTitle(R.string.lock_unlock)
				.setDescription(R.string.lock_unlock_fingerprint_description)
				.setNegativeButton(R.string.lock_unlock_password,
						(dialog, which) -> {
							requestKeyguardUnlock();
						})
				.build();
		CancellationSignal signal = new CancellationSignal();
		biometricPrompt.authenticate(signal, new IAuthenticationCallback() {
			@Override
			public void onAuthenticationError(int errorCode,
					@Nullable CharSequence errString) {
				// when back button is pressed while fingerprint dialog shows
				if (errorCode == BIOMETRIC_ERROR_CANCELED ||
						errorCode == BIOMETRIC_ERROR_USER_CANCELED) {
					finish();
				}
				// locked out due to 5 failed attempts, lasts for 30 seconds
				else if (errorCode == BIOMETRIC_ERROR_LOCKOUT ||
						errorCode == BIOMETRIC_ERROR_LOCKOUT_PERMANENT) {
					if (hasKeyguardLock(UnlockActivity.this)) {
						requestKeyguardUnlock();
					} else {
						// normally fingerprints require a screen lock, but
						// who knows if that's true for all devices out there
						Toast.makeText(UnlockActivity.this, errString,
								Toast.LENGTH_LONG).show();
						finish();
					}
				} else {
					Toast.makeText(UnlockActivity.this, errString,
							Toast.LENGTH_LONG).show();
				}
			}

			@Override
			public void onAuthenticationHelp(int helpCode,
					@Nullable CharSequence helpString) {
			}

			@Override
			public void onAuthenticationSucceeded(
					@NonNull IAuthenticationResult result) {
				unlock();
			}

			@Override
			public void onAuthenticationFailed() {
			}
		});
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
			keyguardShown = true;
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
