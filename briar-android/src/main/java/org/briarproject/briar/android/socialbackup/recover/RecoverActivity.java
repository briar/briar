package org.briarproject.briar.android.socialbackup.recover;

import android.os.Bundle;
import android.widget.Toast;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.socialbackup.ExplainerDismissedListener;
import org.briarproject.briar.android.socialbackup.OwnerRecoveryModeMainFragment;
import org.briarproject.briar.android.socialbackup.ScanQrButtonListener;

public class RecoverActivity extends BaseActivity implements
		BaseFragment.BaseFragmentListener, ExplainerDismissedListener,
		ScanQrButtonListener {

	private int numRecovered;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_recover);

		numRecovered = 0; // TODO - retrieve this from somewhere

		// only show the explainer if we have no shards
		if (numRecovered == 0) {
			OwnerRecoveryModeExplainerFragment fragment =
					new OwnerRecoveryModeExplainerFragment();
			showInitialFragment(fragment);
		} else {
			OwnerRecoveryModeMainFragment fragment =
					OwnerRecoveryModeMainFragment.newInstance(numRecovered);
			showInitialFragment(fragment);
		}
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void explainerDismissed() {
		OwnerRecoveryModeMainFragment fragment =
				OwnerRecoveryModeMainFragment.newInstance(numRecovered);
		showNextFragment(fragment);
	}

	@Override
	public void scanQrButtonClicked() {
		// TODO
		Toast.makeText(this,
				"coming soon...",
				Toast.LENGTH_SHORT).show();
		finish();
	}

	@Override
	@Deprecated
	public void runOnDbThread(Runnable runnable) {
		throw new RuntimeException("Don't use this deprecated method here.");
	}
}
