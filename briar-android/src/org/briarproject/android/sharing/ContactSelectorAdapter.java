package org.briarproject.android.sharing;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.contact.BaseContactListAdapter;
import org.briarproject.api.contact.ContactId;

import java.util.ArrayList;
import java.util.Collection;

class ContactSelectorAdapter extends
		BaseContactListAdapter<SelectableContactItem, SelectableContactHolder> {

	ContactSelectorAdapter(Context context,
			OnContactClickListener<SelectableContactItem> listener) {
		super(context, SelectableContactItem.class, listener);
	}

	@Override
	public SelectableContactHolder onCreateViewHolder(ViewGroup viewGroup,
			int i) {
		View v = LayoutInflater.from(ctx).inflate(
				R.layout.list_item_selectable_contact, viewGroup, false);
		return new SelectableContactHolder(v);
	}

	Collection<ContactId> getSelectedContactIds() {
		Collection<ContactId> selected = new ArrayList<>();

		for (int i = 0; i < items.size(); i++) {
			SelectableContactItem item = items.get(i);
			if (item.isSelected()) selected.add(item.getContact().getId());
		}
		return selected;
	}

}
