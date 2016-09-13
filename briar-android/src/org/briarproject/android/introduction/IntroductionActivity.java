package org.briarproject.android.introduction;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.view.MenuItem;
import android.view.View;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.api.contact.Contact;

// TODO extend the BriarFragmentActivity ?
public class IntroductionActivity extends BriarActivity implements
		BaseFragment.BaseFragmentListener {

	public static final String CONTACT_ID = "briar.CONTACT_ID";

	private int contactId;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		contactId = intent.getIntExtra(CONTACT_ID, -1);
		if (contactId == -1)
			throw new IllegalArgumentException("Wrong ContactId");

		setContentView(R.layout.activity_fragment_container);

		if (savedInstanceState == null) {
			getSupportFragmentManager()
					.beginTransaction()
					.add(R.id.fragmentContainer,
							ContactChooserFragment.newInstance())
					.commit();
		}
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onFragmentCreated(String tag) {

	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onBackPressed() {
		FragmentManager fm = getSupportFragmentManager();
		if (fm.getBackStackEntryCount() == 1) {
			fm.popBackStack();
		} else {
			super.onBackPressed();
		}
	}

	int getContactId() {
		return contactId;
	}

	void showMessageScreen(View view, Contact c1, Contact c2) {

		IntroductionMessageFragment messageFragment =
				IntroductionMessageFragment
						.newInstance(c1.getId().getInt(), c2.getId().getInt());

		if (Build.VERSION.SDK_INT >= 21) {
			messageFragment.setSharedElementEnterTransition(new ChangeBounds());
			messageFragment.setEnterTransition(new Fade());
			messageFragment.setSharedElementReturnTransition(
					new ChangeBounds());
		}

		getSupportFragmentManager()
				.beginTransaction()
				.setCustomAnimations(android.R.anim.fade_in,
						android.R.anim.fade_out,
						android.R.anim.slide_in_left,
						android.R.anim.slide_out_right)
				.addSharedElement(view, "avatar")
				.replace(R.id.fragmentContainer, messageFragment,
						ContactChooserFragment.TAG)
				.addToBackStack(null)
				.commit();
	}
}
