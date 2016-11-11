package org.briarproject.android.contactselection;

import android.support.annotation.UiThread;
import android.view.View;

import org.briarproject.android.contact.BaseContactListAdapter;
import org.briarproject.android.contact.BaseContactListAdapter.OnContactClickListener;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

@UiThread
@NotNullByDefault
public class SelectableContactHolder
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
