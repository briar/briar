package org.briarproject.briar.android.activity;

import android.support.annotation.AnimRes;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;

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

	protected void showMessageDialog(int titleStringId, int msgStringId) {
		// TODO replace with custom dialog fragment ?
		AlertDialog.Builder builder = new AlertDialog.Builder(this,
				R.style.BriarDialogTheme);
		builder.setTitle(titleStringId);
		builder.setMessage(msgStringId);
		builder.setPositiveButton(R.string.ok, null);
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	public void startFragment(BaseFragment fragment,
			boolean isAddedToBackStack) {
		startFragment(fragment, 0, 0, isAddedToBackStack);
	}

	private void startFragment(BaseFragment fragment,
			@AnimRes int inAnimation, @AnimRes int outAnimation,
			boolean isAddedToBackStack) {
		FragmentTransaction trans =
				getSupportFragmentManager().beginTransaction();
		if (inAnimation != 0 && outAnimation != 0) {
			trans.setCustomAnimations(inAnimation, 0, 0, outAnimation);
		}
		trans.replace(R.id.content_fragment, fragment, fragment.getUniqueTag());
		if (isAddedToBackStack) {
			trans.addToBackStack(fragment.getUniqueTag());
		}
		trans.commit();
	}
}
