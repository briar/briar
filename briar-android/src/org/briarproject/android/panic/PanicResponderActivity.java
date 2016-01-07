package org.briarproject.android.panic;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;

import org.briarproject.android.BriarActivity;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.util.FileUtils;
import org.iilab.IilabEngineeringRSA2048Pin;

import java.util.logging.Logger;

import javax.inject.Inject;

import info.guardianproject.GuardianProjectRSA4096;
import info.guardianproject.panic.Panic;
import info.guardianproject.panic.PanicResponder;
import info.guardianproject.trustedintents.TrustedIntents;

public class PanicResponderActivity extends BriarActivity {

	private static final Logger LOG =
			Logger.getLogger(PanicResponderActivity.class.getName());
	@Inject private DatabaseConfig databaseConfig;

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
					if (sharedPref.getBoolean("pref_key_purge", false)) {
						LOG.info("Purging all data...");
						deleteAllData();
					}
					// still sign out if enabled
					else if (sharedPref.getBoolean("pref_key_lock", true)) {
						LOG.info("Signing out...");
						signOut(true);
					}

					// TODO add other panic behavior such as:
					// * send a pre-defined message to certain contacts (#212)
					// * uninstall the app (#211)

				}
				// Performing non-destructive default panic response
				else if (sharedPref.getBoolean("pref_key_lock", true)) {
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

	private void deleteAllData() {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				// TODO somehow delete/shred the database more thoroughly
				FileUtils
						.deleteFileOrDir(
								databaseConfig.getDatabaseDirectory());
				clearSharedPrefs();
				PanicResponder.deleteAllAppData(PanicResponderActivity.this);

				// nothing left to do after everything is deleted,
				// so still sign out
				LOG.info("Signing out...");
				signOut(true);
			}
		});
	}
}