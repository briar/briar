package org.briarproject.android.contact;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.briarproject.R;
import org.briarproject.api.Contact;
import org.briarproject.api.ContactId;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;

public class SelectContactsDialog implements OnMultiChoiceClickListener {

	private Listener listener = null;
	private List<Contact> contacts = null;
	private Set<ContactId> selected = null;

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	public void setContacts(Collection<Contact> contacts) {
		this.contacts = new ArrayList<Contact>(contacts);
	}

	public void setSelected(Collection<ContactId> selected) {
		this.selected = new HashSet<ContactId>(selected);
	}

	public Dialog build(Context ctx) {
		if(listener == null || contacts == null || selected == null)
			throw new IllegalStateException();
		AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
		int size = contacts.size();
		String[] names = new String[size];
		boolean[] checked = new boolean[size];
		for(int i = 0; i < size; i++) {
			Contact c = contacts.get(i);
			names[i] = c.getAuthor().getName();
			checked[i] = selected.contains(c.getId());
		}
		builder.setMultiChoiceItems(names, checked, this);
		builder.setPositiveButton(R.string.done_button,
				new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				listener.contactsSelected(selected);
			}
		});
		builder.setNegativeButton(R.string.cancel_button,
				new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				listener.contactSelectionCancelled();
			}
		});
		return builder.create();
	}

	public void onClick(DialogInterface dialog, int which, boolean isChecked) {
		if(isChecked) selected.add(contacts.get(which).getId());
		else selected.remove(contacts.get(which).getId());
	}

	public interface Listener {

		void contactsSelected(Collection<ContactId> selected);

		void contactSelectionCancelled();
	}
}
