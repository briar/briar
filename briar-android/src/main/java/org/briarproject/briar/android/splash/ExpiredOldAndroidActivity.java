package org.briarproject.briar.android.splash;

import android.content.Context;
import android.os.Bundle;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.Localizer;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;

import javax.annotation.Nullable;

import static android.view.WindowManager.LayoutParams.FLAG_SECURE;
import static org.briarproject.briar.android.TestingConstants.PREVENT_SCREENSHOTS;

@NotNullByDefault
public class ExpiredOldAndroidActivity extends BriarActivity {

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		if (PREVENT_SCREENSHOTS) getWindow().addFlags(FLAG_SECURE);

		setContentView(R.layout.activity_expired_old_android);
		findViewById(R.id.delete_account_button).setOnClickListener(v ->
				signOut(true, true));
	}

	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(
				Localizer.getInstance().setLocale(base));
		Localizer.getInstance().setLocale(this);
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}
}
