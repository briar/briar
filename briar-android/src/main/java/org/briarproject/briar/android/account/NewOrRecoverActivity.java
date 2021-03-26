package org.briarproject.briar.android.account;

import android.content.Intent;
import android.os.Bundle;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.socialbackup.recover.RecoverActivity;
import org.briarproject.briar.android.socialbackup.recover.ReturnShardActivity;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_TASK_ON_HOME;

public class NewOrRecoverActivity extends BaseActivity implements
		BaseFragment.BaseFragmentListener, SetupNewAccountChosenListener,
		RecoverAccountListener {

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		// fade-in after splash screen instead of default animation
		// TODO the fade in is not working
		overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
		setContentView(R.layout.activity_fragment_container);
		NewOrRecoverFragment fragment = NewOrRecoverFragment.newInstance();
		showInitialFragment(fragment);
	}

	@Override
	public void setupNewAccountChosen() {
		finish();
		Intent i = new Intent(this, SetupActivity.class);
		i.addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP |
				FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_TASK_ON_HOME);
		startActivity(i);
	}

	@Override
	public void recoverAccountChosen() {
		finish();
		Intent i = new Intent(this, ReturnShardActivity.class);
		i.addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP |
				FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_TASK_ON_HOME);
		startActivity(i);
	}

	@Override
	@Deprecated
	public void runOnDbThread(Runnable runnable) {
		throw new RuntimeException("Don't use this deprecated method here.");
	}
}
