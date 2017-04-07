package org.briarproject.briar.android.panic;

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

import org.briarproject.briar.R;

import java.util.ArrayList;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import info.guardianproject.panic.Panic;
import info.guardianproject.panic.PanicResponder;

public class PanicPreferencesFragment extends PreferenceFragmentCompat
		implements SharedPreferences.OnSharedPreferenceChangeListener {

	public static final String KEY_LOCK = "pref_key_lock";
	public static final String KEY_PANIC_APP = "pref_key_panic_app";
	public static final String KEY_PURGE = "pref_key_purge";
	public static final String KEY_UNINSTALL = "pref_key_uninstall";

	private static final Logger LOG =
			Logger.getLogger(PanicPreferencesFragment.class.getName());

	private PackageManager pm;
	private CheckBoxPreference lockPref, purgePref, uninstallPref;
	private ListPreference panicAppPref;

	@Override
	public void onCreatePreferences(Bundle bundle, String s) {
		addPreferencesFromResource(R.xml.panic_preferences);

		pm = getActivity().getPackageManager();

		lockPref = (CheckBoxPreference) findPreference(KEY_LOCK);
		panicAppPref = (ListPreference) findPreference(KEY_PANIC_APP);
		purgePref = (CheckBoxPreference) findPreference(KEY_PURGE);
		uninstallPref = (CheckBoxPreference) findPreference(KEY_UNINSTALL);

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

		ArrayList<CharSequence> entries = new ArrayList<>();
		ArrayList<CharSequence> entryValues = new ArrayList<>();
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
							lockPref.setEnabled(false);
							purgePref.setChecked(false);
							purgePref.setEnabled(false);
							uninstallPref.setChecked(false);
							uninstallPref.setEnabled(false);
							getActivity().setResult(Activity.RESULT_CANCELED);
						} else {
							lockPref.setEnabled(true);
							purgePref.setEnabled(true);
							uninstallPref.setEnabled(true);
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
							intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
							if (intent.resolveActivity(
									getActivity().getPackageManager()) !=
									null) {
								startActivity(intent);
							}
							return true;
						}
					});
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		getPreferenceScreen().getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(this);
		showPanicApp(PanicResponder.getTriggerPackageName(getActivity()));
	}

	@Override
	public void onStop() {
		super.onStop();
		getPreferenceScreen().getSharedPreferences()
				.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key.equals(KEY_PURGE)) {
			// enable locking if purging gets enabled
			if (sharedPreferences.getBoolean(KEY_PURGE, false)) {
				lockPref.setChecked(true);
			}
			// disable uninstall if purging gets disabled
			else {
				uninstallPref.setChecked(false);
			}
		}
		// enable purging and locking if uninstall gets enabled
		if (key.equals(KEY_UNINSTALL) &&
				sharedPreferences.getBoolean(KEY_UNINSTALL, false)) {
			lockPref.setChecked(true);
			purgePref.setChecked(true);
		}
		// disable purging and uninstalling if locking gets disabled
		if (key.equals(KEY_LOCK) &&
				!sharedPreferences.getBoolean(KEY_LOCK, true)) {
			purgePref.setChecked(false);
			uninstallPref.setChecked(false);
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

			// disable panic actions
			lockPref.setEnabled(false);
			purgePref.setEnabled(false);
			uninstallPref.setEnabled(false);
		} else {
			// display connected panic app
			try {
				panicAppPref.setValue(triggerPackageName);
				panicAppPref.setSummary(pm.getApplicationLabel(
						pm.getApplicationInfo(triggerPackageName, 0)));
				panicAppPref.setIcon(
						pm.getApplicationIcon(triggerPackageName));

				// enable panic actions
				lockPref.setEnabled(true);
				purgePref.setEnabled(true);
				uninstallPref.setEnabled(true);
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

		AlertDialog.Builder builder = new AlertDialog.Builder(getContext(),
				R.style.BriarDialogTheme);
		builder.setTitle(getString(R.string.dialog_title_connect_panic_app));

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
		builder.setNegativeButton(R.string.allow, okListener);
		builder.setPositiveButton(R.string.cancel, cancelListener);
		builder.show();
	}

	@Nullable
	private String getCallingPackageName() {
		ComponentName componentName = getActivity().getCallingActivity();
		String packageName = null;
		if (componentName != null) {
			packageName = componentName.getPackageName();
		}
		return packageName;
	}

}
