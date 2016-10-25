package org.briarproject.android.sharing;

import android.support.annotation.UiThread;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.contact.BaseContactListAdapter.OnContactClickListener;
import org.briarproject.android.contact.ContactItemViewHolder;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

@UiThread
@NotNullByDefault
class SelectableContactHolder
		extends ContactItemViewHolder<SelectableContactItem> {

	private final CheckBox checkBox;
	private final TextView shared;

	SelectableContactHolder(View v) {
		super(v);
		checkBox = (CheckBox) v.findViewById(R.id.checkBox);
		shared = (TextView) v.findViewById(R.id.infoView);
	}

	@Override
	protected void bind(SelectableContactItem item, @Nullable
			OnContactClickListener<SelectableContactItem> listener) {
		super.bind(item, listener);

		if (item.isSelected()) {
			checkBox.setChecked(true);
		} else {
			checkBox.setChecked(false);
		}

		if (item.isDisabled()) {
			// we share this forum already with that contact
			layout.setEnabled(false);
			shared.setVisibility(VISIBLE);
			grayOutItem(true);
		} else {
			layout.setEnabled(true);
			shared.setVisibility(GONE);
			grayOutItem(false);
		}
	}

	private void grayOutItem(boolean gray) {
		float alpha = gray ? 0.25f : 1f;
		avatar.setAlpha(alpha);
		name.setAlpha(alpha);
		checkBox.setAlpha(alpha);
	}

}
