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
import org.briarproject.android.AndroidComponent;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.api.contact.Contact;

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

		setContentView(R.layout.activity_introduction);

		if (savedInstanceState == null) {
			ContactChooserFragment chooserFragment =
					new ContactChooserFragment();
			getSupportFragmentManager().beginTransaction()
					.add(R.id.introductionContainer, chooserFragment).commit();
		}
	}

	@Override
	public void injectActivity(AndroidComponent component) {
		component.inject(this);
	}

	@Override
	public void showLoadingScreen(boolean isBlocking, int stringId) {
		// this is handled by the recycler view in ContactChooserFragment
	}

	@Override
	public void hideLoadingScreen() {
		// this is handled by the recycler view in ContactChooserFragment
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

	public int getContactId() {
		return contactId;
	}

	public void showMessageScreen(final View view, final Contact c1,
			final Contact c2) {

		IntroductionMessageFragment messageFragment =
				IntroductionMessageFragment
						.newInstance(c1.getId().getInt(), c2.getId().getInt());

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			messageFragment.setSharedElementEnterTransition(new ChangeBounds());
			messageFragment.setEnterTransition(new Fade());
			messageFragment.setSharedElementReturnTransition(new ChangeBounds());
		}

		getSupportFragmentManager().beginTransaction()
				.setCustomAnimations(android.R.anim.fade_in,
						android.R.anim.fade_out,
						android.R.anim.slide_in_left,
						android.R.anim.slide_out_right)
				.addSharedElement(view, "avatar")
				.replace(R.id.introductionContainer, messageFragment,
						ContactChooserFragment.TAG)
				.addToBackStack(null)
				.commit();
	}

}
