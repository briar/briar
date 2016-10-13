package org.briarproject.android.sharing;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.fragment.BaseFragment.BaseFragmentListener;
import org.briarproject.android.sharing.BaseMessageFragment.MessageFragmentListener;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.GroupId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import static android.widget.Toast.LENGTH_SHORT;
import static java.util.logging.Level.WARNING;

public abstract class ShareActivity extends BriarActivity implements
		BaseFragmentListener, ContactSelectorListener, MessageFragmentListener {

	private final static Logger LOG =
			Logger.getLogger(ShareActivity.class.getName());
	final static String CONTACTS = "contacts";

	private volatile GroupId groupId;
	private volatile Collection<ContactId> contacts;

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);

		setContentView(R.layout.activity_fragment_container);

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
		} else {
			ArrayList<Integer> intContacts =
					bundle.getIntegerArrayList(CONTACTS);
			if (intContacts != null) {
				contacts = getContactsFromIntegers(intContacts);
			}
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (contacts != null) {
			outState.putIntegerArrayList(CONTACTS,
					getContactsFromIds(contacts));
		}
	}

	@UiThread
	@Override
	public void contactsSelected(GroupId groupId,
			Collection<ContactId> contacts) {
		this.groupId = groupId;
		this.contacts = contacts;

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

	/**
	 * This must only be called from a DbThread
	 */
	public abstract boolean isDisabled(GroupId groupId, Contact c)
			throws DbException;

	static ArrayList<Integer> getContactsFromIds(
			Collection<ContactId> contacts) {
		// transform ContactIds to Integers so they can be added to a bundle
		ArrayList<Integer> intContacts = new ArrayList<>(contacts.size());
		for (ContactId contactId : contacts) {
			intContacts.add(contactId.getInt());
		}
		return intContacts;
	}

	static Collection<ContactId> getContactsFromIntegers(
			ArrayList<Integer> intContacts) {
		// turn contact integers from a bundle back to ContactIds
		List<ContactId> contacts = new ArrayList<>(intContacts.size());
		for (Integer c : intContacts) {
			contacts.add(new ContactId(c));
		}
		return contacts;
	}

	@UiThread
	@Override
	public void onButtonClick(String message) {
		share(message);
		setResult(RESULT_OK);
		supportFinishAfterTransition();
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

	@Override
	public void onFragmentCreated(String tag) {

	}

}
