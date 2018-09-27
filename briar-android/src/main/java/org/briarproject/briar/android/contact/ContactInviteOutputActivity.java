package org.briarproject.briar.android.contact;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;

import javax.annotation.Nullable;

public class ContactInviteOutputActivity extends BriarActivity implements
		BaseFragmentListener {

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_fragment_container);

		ActionBar ab = getSupportActionBar();
		if (ab != null) {
			ab.setDisplayHomeAsUpEnabled(true);
		}

		if (state == null) {
			showInitialFragment(new ContactLinkOutputFragment());
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	void showLink() {
		showInitialFragment(new ContactLinkOutputFragment());
	}

	void showCode() {
		showNextFragment(new ContactQrCodeOutputFragment());
	}

}
