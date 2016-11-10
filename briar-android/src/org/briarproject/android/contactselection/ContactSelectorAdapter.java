package org.briarproject.android.contactselection;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public class ContactSelectorAdapter extends
		BaseContactSelectorAdapter<SelectableContactItem, SelectableContactHolder> {

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

}
