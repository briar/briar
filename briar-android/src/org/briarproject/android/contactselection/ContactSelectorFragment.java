package org.briarproject.android.contactselection;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.contact.BaseContactListAdapter.OnContactClickListener;
import org.briarproject.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.api.nullsafety.ParametersNotNullByDefault;
import org.jetbrains.annotations.Nullable;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public abstract class ContactSelectorFragment extends
		BaseContactSelectorFragment<SelectableContactItem, SelectableContactHolder>
		implements OnContactClickListener<SelectableContactItem> {

	public static final String TAG = ContactSelectorFragment.class.getName();

	private Menu menu;

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View contentView =
				super.onCreateView(inflater, container, savedInstanceState);
		adapter = new ContactSelectorAdapter(getActivity(), this);
		list.setAdapter(adapter);
		return contentView;
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
