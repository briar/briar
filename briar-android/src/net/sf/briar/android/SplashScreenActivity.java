package net.sf.briar.android;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_TASK_ON_HOME;
import static android.view.Gravity.CENTER;
import net.sf.briar.android.widgets.CommonLayoutParams;
import roboguice.activity.RoboSplashActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

public class SplashScreenActivity extends RoboSplashActivity {

	public SplashScreenActivity() {
		super();
		minDisplayMs = 0;
	}

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(CommonLayoutParams.MATCH_MATCH);
		layout.setGravity(CENTER);
		ProgressBar spinner = new ProgressBar(this);
		spinner.setIndeterminate(true);
		layout.addView(spinner);
		setContentView(layout);
	}

	protected void startNextActivity() {
		Intent i = new Intent(this, HomeScreenActivity.class);
		i.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_TASK_ON_HOME);
		startActivity(i);
	}
}
