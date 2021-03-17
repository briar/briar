package org.briarproject.briar.android.socialbackup;

import android.os.Bundle;
import android.widget.Toast;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.BaseFragment;

public class RecoverActivity extends BaseActivity implements
		BaseFragment.BaseFragmentListener, ExplainerDismissedListener {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_recover);
		OwnerRecoveryModeExplainerFragment fragment = new OwnerRecoveryModeExplainerFragment();
		showInitialFragment(fragment);
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void explainerDismissed () {
		Toast.makeText(this,
				"coming soon...",
				Toast.LENGTH_SHORT).show();
		// TODO go to the next screen in the recover process
		finish();
	}

	@Override
	@Deprecated
	public void runOnDbThread(Runnable runnable) {
		throw new RuntimeException("Don't use this deprecated method here.");
	}
}
