package org.briarproject.briar.android.account;

import android.os.Bundle;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.fragment.BaseFragment;

public class NewOrRecoverActivity extends BaseActivity implements BaseFragment.BaseFragmentListener {

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		// fade-in after splash screen instead of default animation
		overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        setContentView(R.layout.activity_fragment_container);
		NewOrRecoverFragment fragment = NewOrRecoverFragment.newInstance();
		showInitialFragment(fragment);
	}

	@Override
	public void runOnDbThread(Runnable runnable) {

	}
}
