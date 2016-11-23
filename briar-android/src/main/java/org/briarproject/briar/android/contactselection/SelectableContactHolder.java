package org.briarproject.briar.android.contactselection;

import android.support.annotation.UiThread;
import android.view.View;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.contact.BaseContactListAdapter.OnContactClickListener;

import javax.annotation.Nullable;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

@UiThread
@NotNullByDefault
class SelectableContactHolder
		extends BaseSelectableContactHolder<SelectableContactItem> {

	SelectableContactHolder(View v) {
		super(v);
	}

	@Override
	protected void bind(SelectableContactItem item, @Nullable
			OnContactClickListener<SelectableContactItem> listener) {
		super.bind(item, listener);

		if (item.isDisabled()) {
			info.setVisibility(VISIBLE);
		} else {
			info.setVisibility(GONE);
		}
	}

}
