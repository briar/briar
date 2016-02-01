package org.briarproject.android;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.os.StrictMode.VmPolicy;
import android.support.v7.preference.PreferenceManager;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.google.inject.Injector;

import org.briarproject.R;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.util.FileUtils;

import java.util.logging.Logger;

import roboguice.RoboGuice;
import roboguice.activity.RoboSplashActivity;

import static android.view.Gravity.CENTER;
import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static java.util.logging.Level.INFO;
import static org.briarproject.android.TestingConstants.DEFAULT_LOG_LEVEL;
import static org.briarproject.android.TestingConstants.PREVENT_SCREENSHOTS;
import static org.briarproject.android.TestingConstants.TESTING;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;

public class SplashScreenActivity extends RoboSplashActivity {

	private static final Logger LOG =
			Logger.getLogger(SplashScreenActivity.class.getName());

	// This build expires on 1 March 2016
	private static final long EXPIRY_DATE = 1456790400 * 1000L;

	private long now = System.currentTimeMillis();

	public SplashScreenActivity() {
		Logger.getLogger("").setLevel(DEFAULT_LOG_LEVEL);
		enableStrictMode();
		minDisplayMs = 500;
	}

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		if (PREVENT_SCREENSHOTS) getWindow().addFlags(FLAG_SECURE);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setGravity(CENTER);
		layout.setBackgroundColor(Color.WHITE);

		int pad = LayoutUtils.getLargeItemPadding(this);

		ImageView logo = new ImageView(this);
		logo.setPadding(pad, pad, pad, pad);
		logo.setImageResource(R.drawable.briar_logo_large);
		layout.addView(logo);

		setPreferencesDefaults();

		setContentView(layout);
	}

	@Override
	protected void startNextActivity() {
		long duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Guice startup took " + duration + " ms");
		if (System.currentTimeMillis() >= EXPIRY_DATE) {
			LOG.info("Expired");
			startActivity(new Intent(this, ExpiredActivity.class));
		} else {
			SharedPreferences prefs = getSharedPreferences("db", MODE_PRIVATE);
			String hex = prefs.getString("key", null);
			Injector i = RoboGuice.getBaseApplicationInjector(getApplication());
			DatabaseConfig databaseConfig = i.getInstance(DatabaseConfig.class);
			if (hex != null && databaseConfig.databaseExists()) {
				startActivity(new Intent(this, NavDrawerActivity.class));
			} else {
				prefs.edit().clear().apply();
				FileUtils.deleteFileOrDir(
						databaseConfig.getDatabaseDirectory());
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
