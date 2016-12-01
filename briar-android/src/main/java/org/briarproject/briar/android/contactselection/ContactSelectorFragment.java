package org.briarproject.briar.android.contactselection;

import android.content.Context;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.contact.BaseContactListAdapter.OnContactClickListener;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public abstract class ContactSelectorFragment extends
		BaseContactSelectorFragment<SelectableContactItem, ContactSelectorAdapter>
		implements OnContactClickListener<SelectableContactItem> {

	public static final String TAG = ContactSelectorFragment.class.getName();

	private Menu menu;

	@Override
	protected ContactSelectorAdapter getAdapter(Context context,
			OnContactClickListener<SelectableContactItem> listener) {
		return new ContactSelectorAdapter(context, listener);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.contact_selection_actions, menu);
		super.onCreateOptionsMenu(menu, inflater);
		this.menu = menu;
		// hide sharing action initially, if no contact is selected
		onSelectionChanged();
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_contacts_selected:
				selectedContacts = adapter.getSelectedContactIds();
				listener.contactsSelected(selectedContacts);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onSelectionChanged() {
		if (menu == null) return;
		MenuItem item = menu.findItem(R.id.action_contacts_selected);
		if (item == null) return;

		selectedContacts = adapter.getSelectedContactIds();
		if (selectedContacts.size() > 0) {
			item.setVisible(true);
		} else {
			item.setVisible(false);
		}
	}

}
