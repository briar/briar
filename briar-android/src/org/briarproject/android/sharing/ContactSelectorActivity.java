package org.briarproject.android.sharing;

import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.UiThread;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.fragment.BaseFragment.BaseFragmentListener;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.sync.GroupId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class ContactSelectorActivity extends BriarActivity implements
		BaseFragmentListener, ContactSelectorListener {

	final static String CONTACTS = "contacts";

	protected GroupId groupId;
	protected Collection<ContactId> contacts;

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);

		setContentView(R.layout.activity_fragment_container);

		if (bundle != null) {
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

	@CallSuper
	@UiThread
	@Override
	public void contactsSelected(GroupId groupId,
			Collection<ContactId> contacts) {
		this.groupId = groupId;
		this.contacts = contacts;
	}

	@DatabaseExecutor
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

	@Override
	public void onFragmentCreated(String tag) {

	}

}
