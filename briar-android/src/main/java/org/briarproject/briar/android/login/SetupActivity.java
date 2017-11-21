package org.briarproject.briar.android.login;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Bundle;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;
import org.briarproject.briar.android.navdrawer.NavDrawerActivity;

import javax.inject.Inject;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class SetupActivity extends BaseActivity
		implements BaseFragmentListener {

	@Inject
	SetupController setupController;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		// fade-in after splash screen instead of default animation
		overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
		setContentView(R.layout.activity_fragment_container);

		if (state == null) {
			showInitialFragment(AuthorNameFragment.newInstance());
		}
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
		setupController.setSetupActivity(this);
	}

	public void showPasswordFragment() {
		showNextFragment(PasswordFragment.newInstance());
	}

	@TargetApi(23)
	public void showDozeFragment() {
		showNextFragment(DozeFragment.newInstance());
	}

	public void showApp() {
		Intent i = new Intent(this, NavDrawerActivity.class);
		i.setFlags(FLAG_ACTIVITY_NEW_TASK);
		startActivity(i);
		supportFinishAfterTransition();
		overridePendingTransition(R.anim.screen_new_in, R.anim.screen_old_out);
	}

	@Override
	@Deprecated
	public void runOnDbThread(Runnable runnable) {
		throw new RuntimeException("Don't use this deprecated method here.");
	}

}
