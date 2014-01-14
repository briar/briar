package org.briarproject.android;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.view.Gravity.CENTER;
import static java.util.logging.Level.INFO;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;

import java.util.logging.Logger;

import org.briarproject.api.db.DatabaseConfig;

import roboguice.RoboGuice;
import roboguice.activity.RoboSplashActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.google.inject.Injector;

public class SplashScreenActivity extends RoboSplashActivity {

	private static final Logger LOG =
			Logger.getLogger(SplashScreenActivity.class.getName());

	private long start = System.currentTimeMillis();

	public SplashScreenActivity() {
		minDisplayMs = 0;
	}

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setGravity(CENTER);
		ProgressBar spinner = new ProgressBar(this);
		spinner.setIndeterminate(true);
		layout.addView(spinner);
		setContentView(layout);
	}

	protected void startNextActivity() {
		long duration = System.currentTimeMillis() - start;
		if(LOG.isLoggable(INFO))
			LOG.info("Guice startup took " + duration + " ms");
		Injector guice = RoboGuice.getBaseApplicationInjector(getApplication());
		if(guice.getInstance(DatabaseConfig.class).databaseExists()) {
			Intent i = new Intent(this, HomeScreenActivity.class);
			i.setFlags(FLAG_ACTIVITY_NEW_TASK);
			startActivity(i);
		} else {
			Intent i = new Intent(this, SetupActivity.class);
			i.setFlags(FLAG_ACTIVITY_NEW_TASK);
			startActivity(i);
		}
	}
}
