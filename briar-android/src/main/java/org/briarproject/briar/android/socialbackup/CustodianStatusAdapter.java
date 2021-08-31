package org.briarproject.briar.android.socialbackup;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.briar.R;
import org.briarproject.briar.android.contact.BaseContactListAdapter;
import org.briarproject.briar.android.contact.ContactItem;
import org.briarproject.briar.android.contact.ContactItemViewHolder;

public class CustodianStatusAdapter extends BaseContactListAdapter<ContactItem, ContactItemViewHolder<ContactItem>> {

	CustodianStatusAdapter(Context context) {
		super(context, ContactItem.class, null);
	}

	@Override
	public ContactItemViewHolder<ContactItem> onCreateViewHolder(
			ViewGroup viewGroup, int i) {
		View v = LayoutInflater.from(viewGroup.getContext()).inflate(
				R.layout.list_item_contact_small, viewGroup, false);
		return new ContactItemViewHolder<>(v);
	}

}
