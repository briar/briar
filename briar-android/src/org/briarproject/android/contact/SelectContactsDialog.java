package org.briarproject.android.contact;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.briarproject.R;
import org.briarproject.api.Contact;
import org.briarproject.api.ContactId;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

public class SelectContactsDialog extends DialogFragment
implements DialogInterface.OnMultiChoiceClickListener {

	private final Set<ContactId> selected = new HashSet<ContactId>();

	private Listener listener = null;
	private Contact[] contacts = null;

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	public void setContacts(Collection<Contact> contacts) {
		this.contacts = contacts.toArray(new Contact[contacts.size()]);
	}

	@Override
	public Dialog onCreateDialog(Bundle state) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		String[] names = new String[contacts.length];
		for(int i = 0; i < contacts.length; i++)
			names[i] = contacts[i].getAuthor().getName();
		builder.setMultiChoiceItems(names, new boolean[contacts.length], this);
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
		if(isChecked) selected.add(contacts[which].getId());
		else selected.remove(contacts[which].getId());
	}

	public interface Listener {

		void contactsSelected(Collection<ContactId> selected);

		void contactSelectionCancelled();
	}
}
