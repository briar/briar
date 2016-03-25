package org.briarproject.android;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.os.StrictMode.VmPolicy;
import android.support.v7.preference.PreferenceManager;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.briarproject.R;
import org.briarproject.android.util.AndroidUtils;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.db.DatabaseConfig;

import java.util.logging.Logger;

import javax.inject.Inject;

import static android.view.Gravity.CENTER;
import static org.briarproject.android.TestingConstants.DEFAULT_LOG_LEVEL;
import static org.briarproject.android.TestingConstants.TESTING;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;

public class SplashScreenActivity extends BaseActivity {

	private static final Logger LOG =
			Logger.getLogger(SplashScreenActivity.class.getName());

	// This build expires on 1 May 2016
	private static final long EXPIRY_DATE = 1462057200 * 1000L;

	@Inject
	protected DatabaseConfig dbConfig;

	public SplashScreenActivity() {
		Logger.getLogger("").setLevel(DEFAULT_LOG_LEVEL);
		enableStrictMode();
	}

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		setPreferencesDefaults();

		setContentView(R.layout.splash);

		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				startNextActivity();
				finish();
			}
		}, 500);
	}

	@Override
	public void injectActivity(AndroidComponent component) {
		component.inject(this);
	}

	protected void startNextActivity() {
		if (System.currentTimeMillis() >= EXPIRY_DATE) {
			LOG.info("Expired");
			startActivity(new Intent(this, ExpiredActivity.class));
		} else {
			String hex = getEncryptedDatabaseKey();
			if (hex != null && dbConfig.databaseExists()) {
				startActivity(new Intent(this, NavDrawerActivity.class));
			} else {
				clearSharedPrefs();
				AndroidUtils.deleteAppData(this);
				startActivity(new Intent(this, SetupActivity.class));
			}
		}
	}

	private void enableStrictMode() {
		if (TESTING) {
			ThreadPolicy.Builder threadPolicy = new ThreadPolicy.Builder();
			threadPolicy.detectAll();
			threadPolicy.penaltyLog();
			StrictMode.setThreadPolicy(threadPolicy.build());
			VmPolicy.Builder vmPolicy = new VmPolicy.Builder();
			vmPolicy.detectAll();
			vmPolicy.penaltyLog();
			StrictMode.setVmPolicy(vmPolicy.build());
		}
	}

	private void setPreferencesDefaults() {
		new Thread() {
			@Override
			public void run() {
				PreferenceManager.setDefaultValues(SplashScreenActivity.this,
						R.xml.panic_preferences, false);
			}
		}.start();
	}
}
