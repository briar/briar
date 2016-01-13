package org.briarproject.android.panic;

import android.app.Activity;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.text.TextUtils;

import org.briarproject.R;

import java.util.ArrayList;
import java.util.logging.Logger;

import info.guardianproject.panic.Panic;
import info.guardianproject.panic.PanicResponder;

public class PanicPreferencesFragment extends PreferenceFragmentCompat
		implements SharedPreferences.OnSharedPreferenceChangeListener {

	private static final Logger LOG =
			Logger.getLogger(PanicPreferencesFragment.class.getName());

	private PackageManager pm;
	private CheckBoxPreference lockPref;
	private ListPreference panicAppPref;
	private CheckBoxPreference purgePref;

	@Override
	public void onCreatePreferences(Bundle bundle, String s) {
		addPreferencesFromResource(R.xml.panic_preferences);

		pm = getActivity().getPackageManager();

		lockPref = (CheckBoxPreference) findPreference("pref_key_lock");
		panicAppPref = (ListPreference) findPreference("pref_key_panic_app");
		purgePref = (CheckBoxPreference) findPreference("pref_key_purge");

		// check for connect/disconnect intents from panic trigger apps
		if (PanicResponder.checkForDisconnectIntent(getActivity())) {
			LOG.info("Received DISCONNECT intent from Panic Trigger App.");
			// the necessary action should have been performed by the check
			getActivity().finish();
		} else {
			// check if we got a connect intent from a not yet connected app
			String packageName =
					PanicResponder.getConnectIntentSender(getActivity());
			if (!TextUtils.isEmpty((packageName)) &&
					!TextUtils.equals(packageName,
							PanicResponder
									.getTriggerPackageName(getActivity()))) {

				// A new panic trigger app asks us to connect
				LOG.info("Received CONNECT intent from new Panic Trigger App.");

				// Show dialog allowing the user to opt-in
				showOptInDialog();
			}
		}

		ArrayList<CharSequence> entries = new ArrayList<CharSequence>();
		ArrayList<CharSequence> entryValues = new ArrayList<CharSequence>();
		entries.add(0, getString(R.string.panic_app_setting_none));
		entryValues.add(0, Panic.PACKAGE_NAME_NONE);

		for (ResolveInfo resolveInfo : PanicResponder.resolveTriggerApps(pm)) {
			if (resolveInfo.activityInfo == null)
				continue;
			entries.add(resolveInfo.activityInfo.loadLabel(pm));
			entryValues.add(resolveInfo.activityInfo.packageName);
		}

		panicAppPref.setEntries(
				entries.toArray(new CharSequence[entries.size()]));
		panicAppPref.setEntryValues(
				entryValues.toArray(new CharSequence[entryValues.size()]));
		panicAppPref.setDefaultValue(Panic.PACKAGE_NAME_NONE);

		panicAppPref.setOnPreferenceChangeListener(
				new Preference.OnPreferenceChangeListener() {
					@Override
					public boolean onPreferenceChange(Preference preference,
							Object newValue) {
						String packageName = (String) newValue;
						PanicResponder.setTriggerPackageName(getActivity(),
								packageName);
						showPanicApp(packageName);

						if (packageName.equals(Panic.PACKAGE_NAME_NONE)) {
							purgePref.setChecked(false);
							purgePref.setEnabled(false);
							getActivity().setResult(Activity.RESULT_CANCELED);
						} else {
							purgePref.setEnabled(true);
						}

						return true;
					}
				});

		if (entries.size() <= 1) {
			panicAppPref.setOnPreferenceClickListener(
					new Preference.OnPreferenceClickListener() {
						@Override
						public boolean onPreferenceClick(
								Preference preference) {
							Intent intent = new Intent(Intent.ACTION_VIEW);
							intent.setData(Uri.parse(
									"market://details?id=info.guardianproject.ripple"));
							getActivity().startActivity(intent);
							return true;
						}
					});
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		getPreferenceScreen().getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(this);
		showPanicApp(PanicResponder.getTriggerPackageName(getActivity()));
	}

	@Override
	public void onPause() {
		super.onPause();
		getPreferenceScreen().getSharedPreferences()
				.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		// enable locking if purging gets enabled
		if (key.equals("pref_key_purge")
				&& sharedPreferences.getBoolean("pref_key_purge", false)) {
			lockPref.setChecked(true);
		}
		// disable purging if locking gets disabled
		if (key.equals("pref_key_lock")
				&& !sharedPreferences.getBoolean("pref_key_lock",  true)
				&&  sharedPreferences.getBoolean("pref_key_purge", false)) {
			purgePref.setChecked(false);
		}
	}

	private void showPanicApp(String triggerPackageName) {
		if (TextUtils.isEmpty(triggerPackageName)
				|| triggerPackageName.equals(Panic.PACKAGE_NAME_NONE)) {
			// no panic app set
			panicAppPref.setValue(Panic.PACKAGE_NAME_NONE);
			panicAppPref
					.setSummary(getString(R.string.panic_app_setting_summary));
			panicAppPref.setIcon(
					android.R.drawable.ic_menu_close_clear_cancel);
			purgePref.setEnabled(false);
		} else {
			// display connected panic app
			try {
				panicAppPref.setValue(triggerPackageName);
				panicAppPref.setSummary(pm.getApplicationLabel(
						pm.getApplicationInfo(triggerPackageName, 0)));
				panicAppPref.setIcon(
						pm.getApplicationIcon(triggerPackageName));
				purgePref.setEnabled(true);
			} catch (PackageManager.NameNotFoundException e) {
				// revert back to no app, just to be safe
				PanicResponder.setTriggerPackageName(getActivity(),
						Panic.PACKAGE_NAME_NONE);
				showPanicApp(Panic.PACKAGE_NAME_NONE);
			}
		}
	}

	private void showOptInDialog() {
		DialogInterface.OnClickListener okListener =
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog,
							int which) {
						PanicResponder.setTriggerPackageName(getActivity());
						showPanicApp(PanicResponder
								.getTriggerPackageName(getActivity()));
						getActivity().setResult(Activity.RESULT_OK);
					}
				};
		DialogInterface.OnClickListener cancelListener =
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog,
							int which) {
						getActivity().setResult(Activity.RESULT_CANCELED);
						getActivity().finish();
					}
				};

		AlertDialog.Builder builder =
				new AlertDialog.Builder(getContext());
		builder.setTitle(
				getString(R.string.dialog_title_connect_panic_app));

		CharSequence app = getString(R.string.unknown_app);
		String packageName = getCallingPackageName();
		if (packageName != null) {
			try {
				app = pm.getApplicationLabel(
						pm.getApplicationInfo(packageName, 0));
			} catch (PackageManager.NameNotFoundException e) {
				LOG.warning(e.toString());
			}
		}

		String text = String.format(
				getString(R.string.dialog_message_connect_panic_app), app);
		builder.setMessage(text);
		builder.setPositiveButton(android.R.string.ok, okListener);
		builder.setNegativeButton(android.R.string.cancel, cancelListener);
		builder.show();
	}

	private String getCallingPackageName() {
		ComponentName componentName = getActivity().getCallingActivity();
		String packageName = null;
		if (componentName != null) {
			packageName = componentName.getPackageName();
		}
		return packageName;
	}

}
