package org.briarproject.briar.android.panic;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;

import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;

import java.util.logging.Logger;

import javax.inject.Inject;

import info.guardianproject.GuardianProjectRSA4096;
import info.guardianproject.panic.Panic;
import info.guardianproject.panic.PanicResponder;
import info.guardianproject.trustedintents.TrustedIntents;

import static org.briarproject.briar.android.panic.PanicPreferencesFragment.KEY_LOCK;
import static org.briarproject.briar.android.panic.PanicPreferencesFragment.KEY_PURGE;

public class PanicResponderActivity extends BriarActivity {

	private static final Logger LOG =
			Logger.getLogger(PanicResponderActivity.class.getName());

	@Inject
	protected AccountManager accountManager;
	@Inject
	protected AndroidExecutor androidExecutor;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		TrustedIntents trustedIntents = TrustedIntents.get(this);
		// Guardian Project Ripple
		trustedIntents.addTrustedSigner(GuardianProjectRSA4096.class);
		// F-Droid
		trustedIntents.addTrustedSigner(FDroidSignaturePin.class);

		Intent intent = trustedIntents.getIntentFromTrustedSender(this);
		if (intent != null) {
			// received intent from trusted app
			if (Panic.isTriggerIntent(intent)) {
				SharedPreferences sharedPref = PreferenceManager
						.getDefaultSharedPreferences(this);

				LOG.info("Received Panic Trigger...");

				if (PanicResponder.receivedTriggerFromConnectedApp(this)) {
					LOG.info("Panic Trigger came from connected app");

					// Performing panic responses
					if (sharedPref.getBoolean(KEY_PURGE, false)) {
						LOG.info("Purging all data...");
						deleteAllData();
					}
				}
				// non-destructive actions are allowed by non-connected trusted apps
				if (sharedPref.getBoolean(KEY_LOCK, true)) {
					LOG.info("Signing out...");
					signOut(true);
				}
			}
		}

		if (Build.VERSION.SDK_INT >= 21) {
			finishAndRemoveTask();
		} else {
			finish();
		}
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	private void deleteAllData() {
		androidExecutor.runOnBackgroundThread(() -> {
			accountManager.deleteAccount();
			// TODO somehow delete/shred the database more thoroughly
			PanicResponder.deleteAllAppData(PanicResponderActivity.this);

			// nothing left to do after everything is deleted,
			// so still sign out
			LOG.info("Signing out...");
			signOut(true);
		});
	}
}
