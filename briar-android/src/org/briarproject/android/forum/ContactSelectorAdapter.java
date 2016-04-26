package org.briarproject.android.forum;

import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import org.briarproject.R;
import org.briarproject.android.contact.BaseContactListAdapter;
import org.briarproject.android.contact.ContactListItem;
import org.briarproject.api.contact.ContactId;

import java.util.ArrayList;
import java.util.Collection;

public class ContactSelectorAdapter
		extends
		BaseContactListAdapter<ContactSelectorAdapter.SelectableContactHolder> {

	public ContactSelectorAdapter(Context context,
			OnItemClickListener listener) {

		super(context, listener);
	}

	@Override
	public SelectableContactHolder onCreateViewHolder(ViewGroup viewGroup,
			int i) {
		View v = LayoutInflater.from(ctx)
				.inflate(R.layout.list_item_selectable_contact, viewGroup,
						false);

		return new SelectableContactHolder(v);
	}

	@Override
	public void onBindViewHolder(final SelectableContactHolder ui,
			final int position) {
		super.onBindViewHolder(ui, position);

		final SelectableContactListItem item =
				(SelectableContactListItem) getItem(position);

		if (item.isSelected()) {
			ui.checkBox.setChecked(true);
		} else {
			ui.checkBox.setChecked(false);
		}

		if (item.isDisabled()) {
			// we share this forum already with that contact
			ui.layout.setEnabled(false);
			grayOutItem(ui);
		}
	}

	public Collection<ContactId> getSelectedContactIds() {
		Collection<ContactId> selected = new ArrayList<>();

		for (int i = 0; i < contacts.size(); i++) {
			SelectableContactListItem item =
					(SelectableContactListItem) contacts.get(i);
			if (item.isSelected()) selected.add(item.getContact().getId());
		}

		return selected;
	}

	protected static class SelectableContactHolder
			extends BaseContactListAdapter.BaseContactHolder {

		private final CheckBox checkBox;

		public SelectableContactHolder(View v) {
			super(v);

			checkBox = (CheckBox) v.findViewById(R.id.checkBox);
		}
	}

	@Override
	public int compareContactListItems(ContactListItem c1, ContactListItem c2) {
		return compareByName(c1, c2);
	}

	private void grayOutItem(final SelectableContactHolder ui) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			float alpha = 0.25f;
			ui.avatar.setAlpha(alpha);
			ui.name.setAlpha(alpha);
			ui.checkBox.setAlpha(alpha);
		} else {
			ColorFilter colorFilter = new PorterDuffColorFilter(Color.GRAY,
					PorterDuff.Mode.MULTIPLY);
			ui.avatar.setColorFilter(colorFilter);
			ui.name.setEnabled(false);
			ui.checkBox.setEnabled(false);
		}
	}

}
