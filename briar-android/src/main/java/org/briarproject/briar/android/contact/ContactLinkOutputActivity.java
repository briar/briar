package org.briarproject.briar.android.contact;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.Menu;
import android.view.MenuItem;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;

import javax.annotation.Nullable;

public class ContactLinkOutputActivity extends BriarActivity implements
		BaseFragmentListener {

	private Menu menu;
	private boolean showQrCode = true;

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
			showInitialFragment(new ContactQrCodeOutputFragment());
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
//		MenuInflater inflater = getMenuInflater();
//		inflater.inflate(R.menu.contact_output_actions, menu);
//		menu.findItem(R.id.action_switch)
//				.setTitle(showQrCode ? R.string.show_link : R.string.show_code);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
			case R.id.action_switch:
				switchFragment();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void switchFragment() {
		if (showQrCode) {
			showInitialFragment(new ContactLinkOutputFragment());
		} else {
			showInitialFragment(new ContactQrCodeOutputFragment());
		}
		showQrCode = !showQrCode;
	}

}
