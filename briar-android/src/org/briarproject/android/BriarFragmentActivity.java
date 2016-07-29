package org.briarproject.android;

import android.support.annotation.AnimRes;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;

import org.briarproject.R;
import org.briarproject.android.blogs.BlogsFragment;
import org.briarproject.android.contact.ContactListFragment;
import org.briarproject.android.forum.ForumListFragment;
import org.briarproject.android.fragment.BaseFragment;

/**
 * This class should be extended by classes that wish to utilise fragments in
 * Briar, it encapsulates all fragment related code.
 */
public abstract class BriarFragmentActivity extends BriarActivity {

	private void updateToolbarTitle(String fragmentTag) {
		ActionBar actionBar = getSupportActionBar();
		if (actionBar == null)
			return;

		if (fragmentTag.equals(ContactListFragment.TAG)) {
			actionBar.setTitle(R.string.contacts_toolbar_header);
		} else if (fragmentTag.equals(ForumListFragment.TAG)) {
			actionBar.setTitle(R.string.forums_toolbar_header);
		} else if (fragmentTag.equals(BlogsFragment.TAG)) {
			actionBar.setTitle(R.string.blogs_button);
		}
	}

	void clearBackStack() {
		getSupportFragmentManager()
				.popBackStackImmediate(
						null,
						FragmentManager.POP_BACK_STACK_INCLUSIVE
				);
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
		updateToolbarTitle(tag);
	}

	protected void startFragment(BaseFragment fragment) {
		if (getSupportFragmentManager().getBackStackEntryCount() == 0)
			startFragment(fragment, false);
		else startFragment(fragment, true);
	}

	void showMessageDialog(int titleStringId, int msgStringId) {
		// TODO replace with custom dialog fragment ?
		AlertDialog.Builder builder = new AlertDialog.Builder(this,
				R.style.BriarDialogTheme);
		builder.setTitle(titleStringId);
		builder.setMessage(msgStringId);
		builder.setPositiveButton(R.string.dialog_button_ok, null);
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	private void startFragment(BaseFragment fragment,
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
