package org.briarproject.briar.android.sharing;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.briar.R;
import org.briarproject.briar.android.contact.BaseContactListAdapter;
import org.briarproject.briar.android.contact.ContactItem;
import org.briarproject.briar.android.contact.ContactItemViewHolder;

import androidx.annotation.NonNull;

class SharingStatusAdapter extends
		BaseContactListAdapter<ContactItem, ContactItemViewHolder<ContactItem>> {

	SharingStatusAdapter(Context context) {
		super(context, ContactItem.class, null);
	}

	@NonNull
	@Override
	public ContactItemViewHolder<ContactItem> onCreateViewHolder(
			@NonNull ViewGroup viewGroup, int i) {
		View v = LayoutInflater.from(viewGroup.getContext()).inflate(
				R.layout.list_item_contact_small, viewGroup, false);
		return new ContactItemViewHolder<>(v);
	}

}
