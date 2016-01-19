package org.briarproject.android;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT;
import static org.briarproject.android.TestingConstants.PREVENT_SCREENSHOTS;

public abstract class BaseActivity extends AppCompatActivity {

	public final static String PREFS_NAME = "db";
	public final static String PREF_DB_KEY = "key";
	public final static String PREF_SEEN_WELCOME_MESSAGE = "welcome_message";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (PREVENT_SCREENSHOTS) getWindow().addFlags(FLAG_SECURE);

		BriarApplication application = (BriarApplication) getApplication();
		injectActivity(application.getApplicationComponent());
	}

	public abstract void injectActivity(AndroidComponent component);

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

	public void hideSoftKeyboard(View view) {
		IBinder token = view.getWindowToken();
		Object o = getSystemService(INPUT_METHOD_SERVICE);
		((InputMethodManager) o).hideSoftInputFromWindow(token, 0);
	}
}
