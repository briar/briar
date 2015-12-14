package org.briarproject.android;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
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
import roboguice.inject.ContentViewListener;
import roboguice.inject.RoboInjector;
import roboguice.util.RoboContext;

import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static android.view.inputmethod.InputMethodManager.HIDE_IMPLICIT_ONLY;
import static org.briarproject.android.TestingConstants.PREVENT_SCREENSHOTS;

public abstract class BaseActivity extends AppCompatActivity implements RoboContext {

	private final static String PREFS_DB = "db";
	private final static String KEY_DB_KEY = "key";

	protected EventManager eventManager;
	protected HashMap<Key<?>, Object> scopedObjects = new HashMap();
	@Inject
	ContentViewListener ignored;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		RoboInjector injector = RoboGuice.getInjector(this);
		eventManager = (EventManager) injector.getInstance(EventManager.class);
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
		eventManager.fire(new OnConfigurationChangedEvent(currentConfig, newConfig));
	}

	public void onContentChanged() {
		super.onContentChanged();
		RoboGuice.getInjector(this).injectViewMembers(this);
		eventManager.fire(new OnContentChangedEvent());
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		eventManager.fire(new OnActivityResultEvent(requestCode, resultCode, data));
	}

	@Override
	public Map<Key<?>, Object> getScopedObjectMap() {
		return scopedObjects;
	}

	private SharedPreferences getBriarPrefs(String prefsName) {
		return getSharedPreferences(prefsName, MODE_PRIVATE);
	}

	protected String getDbKeyInHex() {
		return getBriarPrefs(PREFS_DB).getString(KEY_DB_KEY, null);
	}

	private void clearPrefs(String prefsName) {
		SharedPreferences.Editor editor = getBriarPrefs(prefsName).edit();
		editor.clear();
		editor.apply();
	}

	protected void clearDbPrefs() {
		this.clearPrefs(PREFS_DB);
	}

	protected void gotoAndFinish(Class classInstance, int resultCode) {
		if (resultCode != Integer.MIN_VALUE)
			setResult(resultCode);
		startActivity(new Intent(this, classInstance));
		finish();
	}

	protected void gotoAndFinish(Class classInstance) {
		gotoAndFinish(classInstance, Integer.MIN_VALUE);
	}

	protected void hideSoftKeyboard() {
		Object o = getSystemService(INPUT_METHOD_SERVICE);
		((InputMethodManager) o).toggleSoftInput(HIDE_IMPLICIT_ONLY, 0);
	}
}
