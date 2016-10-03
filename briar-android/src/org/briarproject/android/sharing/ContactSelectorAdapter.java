package org.briarproject.android.sharing;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.contact.BaseContactListAdapter;
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
				(SelectableContactListItem) getItemAt(position);
		if (item == null) return;

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
			ui.layout.setEnabled(true);
			ui.shared.setVisibility(GONE);
			grayOutItem(ui, false);
		}
	}

	Collection<ContactId> getSelectedContactIds() {
		Collection<ContactId> selected = new ArrayList<>();

		for (int i = 0; i < items.size(); i++) {
			SelectableContactListItem item =
					(SelectableContactListItem) items.get(i);
			if (item.isSelected()) selected.add(item.getContact().getId());
		}

		return selected;
	}

	static class SelectableContactHolder
			extends BaseContactListAdapter.BaseContactHolder {

		private final CheckBox checkBox;
		private final TextView shared;

		private SelectableContactHolder(View v) {
			super(v);

			checkBox = (CheckBox) v.findViewById(R.id.checkBox);
			shared = (TextView) v.findViewById(R.id.infoView);
		}
	}

	private void grayOutItem(SelectableContactHolder ui, boolean gray) {
		float alpha = gray ? 0.25f : 1f;
		ui.avatar.setAlpha(alpha);
		ui.name.setAlpha(alpha);
		ui.checkBox.setAlpha(alpha);
	}
}
