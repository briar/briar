package org.briarproject.android.panic;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;

import org.briarproject.android.ActivityComponent;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.api.AndroidExecutor;
import org.briarproject.android.controller.ConfigController;
import org.briarproject.android.util.AndroidUtils;
import org.iilab.IilabEngineeringRSA2048Pin;

import java.util.logging.Logger;

import javax.inject.Inject;

import info.guardianproject.GuardianProjectRSA4096;
import info.guardianproject.panic.Panic;
import info.guardianproject.panic.PanicResponder;
import info.guardianproject.trustedintents.TrustedIntents;

import static android.content.Intent.ACTION_DELETE;
import static org.briarproject.android.panic.PanicPreferencesFragment.KEY_LOCK;
import static org.briarproject.android.panic.PanicPreferencesFragment.KEY_PURGE;
import static org.briarproject.android.panic.PanicPreferencesFragment.KEY_UNINSTALL;

public class PanicResponderActivity extends BriarActivity {

	private static final Logger LOG =
			Logger.getLogger(PanicResponderActivity.class.getName());
	@Inject protected ConfigController configController;
	@Inject
	protected AndroidExecutor androidExecutor;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		TrustedIntents trustedIntents = TrustedIntents.get(this);
		// Guardian Project Ripple
		trustedIntents.addTrustedSigner(GuardianProjectRSA4096.class);
		// Amnesty International's Panic Button, made by iilab.org
		trustedIntents.addTrustedSigner(IilabEngineeringRSA2048Pin.class);

		Intent intent = trustedIntents.getIntentFromTrustedSender(this);
		if (intent != null) {
			// received intent from trusted app
			if (Panic.isTriggerIntent(intent)) {
				SharedPreferences sharedPref = PreferenceManager
						.getDefaultSharedPreferences(this);

				LOG.info("Received Panic Trigger...");

				if (PanicResponder.receivedTriggerFromConnectedApp(this)) {
					LOG.info("Panic Trigger came from connected app.");
					LOG.info("Performing destructive responses...");

					// Performing destructive panic responses
					if (sharedPref.getBoolean(KEY_UNINSTALL, false)) {
						LOG.info("Purging all data...");
						deleteAllData();

						LOG.info("Uninstalling...");
						Intent uninstall = new Intent(ACTION_DELETE);
						uninstall.setData(
								Uri.parse("package:" + getPackageName()));
						startActivity(uninstall);
					} else if (sharedPref.getBoolean(KEY_PURGE, false)) {
						LOG.info("Purging all data...");
						deleteAllData();
					} else if (sharedPref.getBoolean(KEY_LOCK, true)) {
						LOG.info("Signing out...");
						signOut(true);
					}

					// TODO send a pre-defined message to certain contacts (#212)
				}
				// Performing non-destructive default panic response
				else if (sharedPref.getBoolean(KEY_LOCK, true)) {
					LOG.info("Signing out...");
					signOut(true);
				}
			}
		}
		// received intent from non-trusted app
		else {
			intent = getIntent();
			if (intent != null && Panic.isTriggerIntent(intent)) {
				LOG.info("Signing out...");
				signOut(true);
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
		androidExecutor.execute(new Runnable() {
			public void run() {
				configController.clearPrefs();
				// TODO somehow delete/shred the database more thoroughly
				// TODO replace this static call with a controller method
				AndroidUtils.deleteAppData(PanicResponderActivity.this);
				PanicResponder.deleteAllAppData(PanicResponderActivity.this);

				// nothing left to do after everything is deleted,
				// so still sign out
				LOG.info("Signing out...");
				signOut(true);
			}
		});
	}
}