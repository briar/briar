package org.briarproject.android;

import static android.view.Gravity.CENTER;
import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;

import org.briarproject.R;
import org.briarproject.android.util.LayoutUtils;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ExpiredActivity extends Activity {

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		getWindow().setFlags(FLAG_SECURE, FLAG_SECURE);

		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setGravity(CENTER);

		int pad = LayoutUtils.getPadding(this);

		TextView warning = new TextView(this);
		warning.setGravity(CENTER);
		warning.setTextSize(18);
		warning.setPadding(pad, pad, pad, pad);
		warning.setText(R.string.expiry_warning);
		layout.addView(warning);

		setContentView(layout);
	}
}
