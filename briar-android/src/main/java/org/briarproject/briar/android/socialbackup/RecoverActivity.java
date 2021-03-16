package org.briarproject.briar.android.socialbackup;

import android.os.Bundle;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.BaseFragment;

public class RecoverActivity extends BriarActivity implements
		BaseFragment.BaseFragmentListener {

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
}
