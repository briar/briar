package org.briarproject.android.sharing;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.GroupId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class ShareActivity extends BriarActivity implements
		BaseFragment.BaseFragmentListener {

	final static String CONTACTS = "contacts";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_share);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException("No GroupId");
		GroupId groupId = new GroupId(b);

		if (savedInstanceState == null) {
			ContactSelectorFragment contactSelectorFragment =
					ContactSelectorFragment.newInstance(groupId);
			getSupportFragmentManager().beginTransaction()
					.add(R.id.shareContainer, contactSelectorFragment)
					.commit();
		}
	}

	abstract ShareMessageFragment getMessageFragment(GroupId groupId,
			Collection<ContactId> contacts);

	abstract boolean isDisabled(GroupId groupId, Contact c) throws DbException;

	void showMessageScreen(GroupId groupId, Collection<ContactId> contacts) {
		ShareMessageFragment messageFragment =
				getMessageFragment(groupId, contacts);

		getSupportFragmentManager().beginTransaction()
				.setCustomAnimations(android.R.anim.fade_in,
						android.R.anim.fade_out,
						android.R.anim.slide_in_left,
						android.R.anim.slide_out_right)
				.replace(R.id.shareContainer, messageFragment,
						ContactSelectorFragment.TAG)
				.addToBackStack(null)
				.commit();
	}

	static ArrayList<Integer> getContactsFromIds(
			Collection<ContactId> contacts) {

		// transform ContactIds to Integers so they can be added to a bundle
		ArrayList<Integer> intContacts = new ArrayList<>(contacts.size());
		for (ContactId contactId : contacts) {
			intContacts.add(contactId.getInt());
		}
		return intContacts;
	}

	void sharingSuccessful(View v) {
		setResult(RESULT_OK);
		hideSoftKeyboard(v);
		supportFinishAfterTransition();
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

	@Override
	public void onFragmentCreated(String tag) {

	}

}
