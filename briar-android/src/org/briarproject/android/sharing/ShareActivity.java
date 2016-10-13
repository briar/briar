package org.briarproject.android.sharing;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.sharing.BaseMessageFragment.MessageFragmentListener;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;
import java.util.logging.Logger;

import static android.widget.Toast.LENGTH_SHORT;
import static java.util.logging.Level.WARNING;

public abstract class ShareActivity extends ContactSelectorActivity implements
		MessageFragmentListener {

	private final static Logger LOG =
			Logger.getLogger(ShareActivity.class.getName());

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException("No GroupId");
		groupId = new GroupId(b);

		if (bundle == null) {
			ContactSelectorFragment contactSelectorFragment =
					ContactSelectorFragment.newInstance(groupId);
			getSupportFragmentManager().beginTransaction()
					.add(R.id.fragmentContainer, contactSelectorFragment)
					.commit();
		}
	}

	@UiThread
	@Override
	public void contactsSelected(GroupId groupId,
			Collection<ContactId> contacts) {
		super.contactsSelected(groupId, contacts);

		BaseMessageFragment messageFragment = getMessageFragment();
		getSupportFragmentManager().beginTransaction()
				.setCustomAnimations(android.R.anim.fade_in,
						android.R.anim.fade_out,
						android.R.anim.slide_in_left,
						android.R.anim.slide_out_right)
				.replace(R.id.fragmentContainer, messageFragment,
						ContactSelectorFragment.TAG)
				.addToBackStack(null)
				.commit();
	}

	abstract BaseMessageFragment getMessageFragment();

	@UiThread
	@Override
	public boolean onButtonClick(String message) {
		share(message);
		setResult(RESULT_OK);
		supportFinishAfterTransition();
		return true;
	}

	private void share(final String msg) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					for (ContactId c : contacts) {
						share(groupId, c, msg);
					}
				} catch (DbException e) {
					// TODO proper error handling
					sharingError();
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	/**
	 * This method must be run from the DbThread.
	 */
	protected abstract void share(GroupId g, ContactId c, String msg)
			throws DbException;

	private void sharingError() {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				int res = getSharingError();
				Toast.makeText(ShareActivity.this, res, LENGTH_SHORT).show();
			}
		});
	}

	protected abstract @StringRes int getSharingError();

}
