package org.briarproject.briar.android.socialbackup;

import android.os.Bundle;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.BaseFragment;

public class CustodianHelpRecoverActivity extends BriarActivity implements
		BaseFragment.BaseFragmentListener, CustodianScanQrButtonListener {
	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_recover); // TODO change this
        // TODO check if we have a shard for this secret owner
		// if not we should not even display the menu item
		CustodianRecoveryModeExplainerFragment fragment =
				new CustodianRecoveryModeExplainerFragment();
		showInitialFragment(fragment);
	}

	@Override
	public void scanQrButtonClicked() {
		// TODO scan qr code
		finish();
	}
}
