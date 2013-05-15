package net.sf.briar.android;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.view.Gravity.CENTER;
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_MATCH;
import net.sf.briar.api.db.DatabaseConfig;
import roboguice.RoboGuice;
import roboguice.activity.RoboSplashActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.google.inject.Injector;

public class SplashScreenActivity extends RoboSplashActivity {

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
