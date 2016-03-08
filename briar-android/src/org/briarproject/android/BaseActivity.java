package org.briarproject.android;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.google.inject.Inject;
import com.google.inject.Key;

import java.util.HashMap;
import java.util.Map;

import roboguice.RoboGuice;
import roboguice.activity.event.OnActivityResultEvent;
import roboguice.activity.event.OnConfigurationChangedEvent;
import roboguice.activity.event.OnContentChangedEvent;
import roboguice.activity.event.OnCreateEvent;
import roboguice.activity.event.OnDestroyEvent;
import roboguice.activity.event.OnNewIntentEvent;
import roboguice.activity.event.OnPauseEvent;
import roboguice.activity.event.OnRestartEvent;
import roboguice.activity.event.OnResumeEvent;
import roboguice.activity.event.OnStartEvent;
import roboguice.activity.event.OnStopEvent;
import roboguice.event.EventManager;
import roboguice.inject.RoboInjector;
import roboguice.util.RoboContext;

import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT;
import static org.briarproject.android.TestingConstants.PREVENT_SCREENSHOTS;

public abstract class BaseActivity extends AppCompatActivity
		implements RoboContext {

	public final static String PREFS_NAME = "db";
	public final static String PREF_DB_KEY = "key";
	public final static String PREF_SEEN_WELCOME_MESSAGE = "welcome_message";

	private final HashMap<Key<?>, Object> scopedObjects =
			new HashMap<Key<?>, Object>();

	@Inject private EventManager eventManager;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		RoboInjector injector = RoboGuice.getInjector(this);
		injector.injectMembersWithoutViews(this);
		super.onCreate(savedInstanceState);
		eventManager.fire(new OnCreateEvent(savedInstanceState));

		if (PREVENT_SCREENSHOTS) getWindow().addFlags(FLAG_SECURE);
	}

	protected void onRestart() {
		super.onRestart();
		eventManager.fire(new OnRestartEvent());
	}

	protected void onStart() {
		super.onStart();
		eventManager.fire(new OnStartEvent());
	}

	protected void onResume() {
		super.onResume();
		eventManager.fire(new OnResumeEvent());
	}

	protected void onPause() {
		super.onPause();
		eventManager.fire(new OnPauseEvent());
	}

	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		eventManager.fire(new OnNewIntentEvent());
	}

	protected void onStop() {
		try {
			eventManager.fire(new OnStopEvent());
		} finally {
			super.onStop();
		}

	}

	protected void onDestroy() {
		try {
			eventManager.fire(new OnDestroyEvent());
		} finally {
			try {
				RoboGuice.destroyInjector(this);
			} finally {
				super.onDestroy();
			}
		}

	}

	public void onConfigurationChanged(Configuration newConfig) {
		Configuration currentConfig = getResources().getConfiguration();
		super.onConfigurationChanged(newConfig);
		eventManager.fire(new OnConfigurationChangedEvent(currentConfig,
				newConfig));
	}

	public void onContentChanged() {
		super.onContentChanged();
		RoboGuice.getInjector(this).injectViewMembers(this);
		eventManager.fire(new OnContentChangedEvent());
	}

	protected void onActivityResult(int requestCode, int resultCode,
			Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		eventManager.fire(new OnActivityResultEvent(requestCode, resultCode,
				data));
	}

	@Override
	public Map<Key<?>, Object> getScopedObjectMap() {
		return scopedObjects;
	}

	private SharedPreferences getSharedPrefs() {
		return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
	}

	protected String getEncryptedDatabaseKey() {
		return getSharedPrefs().getString(PREF_DB_KEY, null);
	}

	protected void storeEncryptedDatabaseKey(final String hex) {
		SharedPreferences.Editor editor = getSharedPrefs().edit();
		editor.putString(PREF_DB_KEY, hex);
		editor.apply();
	}

	protected void clearSharedPrefs() {
		SharedPreferences.Editor editor = getSharedPrefs().edit();
		editor.clear();
		editor.apply();
	}

	protected void showSoftKeyboard(View view) {
		Object o = getSystemService(INPUT_METHOD_SERVICE);
		((InputMethodManager) o).showSoftInput(view, SHOW_IMPLICIT);
	}

	protected void hideSoftKeyboard(View view) {
		IBinder token = view.getWindowToken();
		Object o = getSystemService(INPUT_METHOD_SERVICE);
		((InputMethodManager) o).hideSoftInputFromWindow(token, 0);
	}
}
