package org.briarproject.android;

import static android.view.inputmethod.InputMethodManager.HIDE_IMPLICIT_ONLY;
import static org.briarproject.android.TestingConstants.PREVENT_SCREENSHOTS;
import static android.view.WindowManager.LayoutParams.FLAG_SECURE;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;

import roboguice.activity.RoboActivity;

public abstract class BaseActivity extends RoboActivity {

	private final static String PREFS_DB = "db";
	private final static String KEY_DB_KEY = "key";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (PREVENT_SCREENSHOTS) getWindow().addFlags(FLAG_SECURE);
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
