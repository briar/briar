package org.briarproject.briar.android.activity;

import android.support.v4.app.FragmentTransaction;

import org.briarproject.briar.R;
import org.briarproject.briar.android.contact.ContactListFragment;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.navdrawer.NavDrawerActivity;

import static android.support.v4.app.FragmentManager.POP_BACK_STACK_INCLUSIVE;

/**
 * This class should be extended by classes that wish to utilise fragments in
 * Briar, it encapsulates all fragment related code.
 */
public abstract class BriarFragmentActivity extends BriarActivity {

	protected void clearBackStack() {
		getSupportFragmentManager().popBackStackImmediate(null,
				POP_BACK_STACK_INCLUSIVE);
	}

	@Override
	public void onBackPressed() {
		if (this instanceof NavDrawerActivity &&
				getSupportFragmentManager().getBackStackEntryCount() == 0 &&
				getSupportFragmentManager()
						.findFragmentByTag(ContactListFragment.TAG) == null) {
			/*
			This Makes sure that the first fragment (ContactListFragment) the
			user sees is the same as the last fragment the user sees before
			exiting. This models the typical Google navigation behaviour such
			as in Gmail/Inbox.
			 */
			startFragment(ContactListFragment.newInstance());
		} else {
			super.onBackPressed();
		}
	}

	public void onFragmentCreated(String tag) {
	}

	protected void startFragment(BaseFragment fragment) {
		if (getSupportFragmentManager().getBackStackEntryCount() == 0)
			startFragment(fragment, false);
		else startFragment(fragment, true);
	}

	protected void startFragment(BaseFragment fragment,
			boolean isAddedToBackStack) {
		FragmentTransaction trans =
				getSupportFragmentManager().beginTransaction()
						.setCustomAnimations(R.anim.dialog_in,
								R.anim.dialog_out, R.anim.dialog_in,
								R.anim.dialog_out)
						.replace(R.id.fragmentContainer, fragment,
								fragment.getUniqueTag());
		if (isAddedToBackStack) {
			trans.addToBackStack(fragment.getUniqueTag());
		}
		trans.commit();
	}
}
