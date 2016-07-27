package org.briarproject.android.forum;

import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.contact.BaseContactListAdapter;
import org.briarproject.android.contact.ContactListItem;
import org.briarproject.api.contact.ContactId;

import java.util.ArrayList;
import java.util.Collection;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

class ContactSelectorAdapter
		extends BaseContactListAdapter<ContactSelectorAdapter.SelectableContactHolder> {

	ContactSelectorAdapter(Context context,
			OnItemClickListener listener) {

		super(context, listener);
	}

	@Override
	public SelectableContactHolder onCreateViewHolder(ViewGroup viewGroup,
			int i) {
		View v = LayoutInflater.from(ctx).inflate(
				R.layout.list_item_selectable_contact, viewGroup, false);

		return new SelectableContactHolder(v);
	}

	@Override
	public void onBindViewHolder(SelectableContactHolder ui, int position) {
		super.onBindViewHolder(ui, position);

		SelectableContactListItem item =
				(SelectableContactListItem) getItem(position);

		if (item.isSelected()) {
			ui.checkBox.setChecked(true);
		} else {
			ui.checkBox.setChecked(false);
		}

		if (item.isDisabled()) {
			// we share this forum already with that contact
			ui.layout.setEnabled(false);
			ui.shared.setVisibility(VISIBLE);
			grayOutItem(ui, true);
		} else {
			ui.shared.setVisibility(GONE);
			grayOutItem(ui, false);
		}
	}

	Collection<ContactId> getSelectedContactIds() {
		Collection<ContactId> selected = new ArrayList<>();

		for (int i = 0; i < contacts.size(); i++) {
			SelectableContactListItem item =
					(SelectableContactListItem) contacts.get(i);
			if (item.isSelected()) selected.add(item.getContact().getId());
		}

		return selected;
	}

	static class SelectableContactHolder
			extends BaseContactListAdapter.BaseContactHolder {

		private final CheckBox checkBox;
		private final TextView shared;

		SelectableContactHolder(View v) {
			super(v);

			checkBox = (CheckBox) v.findViewById(R.id.checkBox);
			shared = (TextView) v.findViewById(R.id.infoView);
		}
	}

	@Override
	public int compareContactListItems(ContactListItem c1, ContactListItem c2) {
		return compareByName(c1, c2);
	}

	private void grayOutItem(final SelectableContactHolder ui,
			final boolean gray) {
		if (Build.VERSION.SDK_INT >= 11) {
			float alpha = 1f;
			if (gray) alpha = 0.25f;
			ui.avatar.setAlpha(alpha);
			ui.name.setAlpha(alpha);
			ui.shared.setAlpha(alpha);
			ui.checkBox.setAlpha(alpha);
		} else {
			if (gray) {
				ColorFilter colorFilter = new PorterDuffColorFilter(
						ContextCompat.getColor(ctx, R.color.window_background),
						PorterDuff.Mode.MULTIPLY);
				ui.avatar.setColorFilter(colorFilter);
			} else{
				ui.avatar.clearColorFilter();
			}
			ui.name.setEnabled(!gray);
			ui.shared.setEnabled(!gray);
			ui.checkBox.setEnabled(!gray);
		}
	}
}
