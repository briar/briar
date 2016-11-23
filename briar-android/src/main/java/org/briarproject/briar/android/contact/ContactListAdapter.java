package org.briarproject.briar.android.contact;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.briar.R;

public class ContactListAdapter extends
		BaseContactListAdapter<ContactListItem, ContactListItemViewHolder> {

	public ContactListAdapter(Context context,
			OnContactClickListener<ContactListItem> listener) {
		super(context, ContactListItem.class, listener);
	}

	@Override
	public ContactListItemViewHolder onCreateViewHolder(ViewGroup viewGroup,
			int i) {
		View v = LayoutInflater.from(viewGroup.getContext()).inflate(
				R.layout.list_item_contact, viewGroup, false);

		return new ContactListItemViewHolder(v);
	}

	@Override
	public boolean areContentsTheSame(ContactListItem c1, ContactListItem c2) {
		// check for all properties that influence visual
		// representation of contact
		if (c1.getUnreadCount() != c2.getUnreadCount()) {
			return false;
		}
		if (c1.getTimestamp() != c2.getTimestamp()) {
			return false;
		}
		return c1.isConnected() == c2.isConnected();
	}

	@Override
	public int compare(ContactListItem c1, ContactListItem c2) {
		long time1 = c1.getTimestamp();
		long time2 = c2.getTimestamp();
		if (time1 < time2) return 1;
		if (time1 > time2) return -1;
		return 0;
	}

}
