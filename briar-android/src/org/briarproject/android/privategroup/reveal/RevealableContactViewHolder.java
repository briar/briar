package org.briarproject.android.privategroup.reveal;

import android.support.annotation.UiThread;
import android.view.View;
import android.widget.ImageView;

import org.briarproject.R;
import org.briarproject.android.contact.BaseContactListAdapter.OnContactClickListener;
import org.briarproject.android.contactselection.BaseSelectableContactHolder;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static org.briarproject.android.util.AndroidUtils.GREY_OUT;
import static org.briarproject.api.privategroup.Visibility.INVISIBLE;

@UiThread
@NotNullByDefault
public class RevealableContactViewHolder
		extends BaseSelectableContactHolder<RevealableContactItem> {

	private final ImageView icon;

	RevealableContactViewHolder(View v) {
		super(v);

		icon = (ImageView) v.findViewById(R.id.visibilityView);
	}

	@Override
	protected void bind(RevealableContactItem item, @Nullable
			OnContactClickListener<RevealableContactItem> listener) {
		super.bind(item, listener);

		switch (item.getVisibility()) {
			case VISIBLE:
				info.setText(R.string.groups_reveal_visible);
				break;
			case REVEALED_BY_US:
				info.setText(R.string.groups_reveal_visible_revealed_by_us);
				break;
			case REVEALED_BY_CONTACT:
				info.setText(
						R.string.groups_reveal_visible_revealed_by_contact);
				break;
			case INVISIBLE:
				info.setText(R.string.groups_reveal_invisible);
				break;
		}

		if (item.getVisibility() == INVISIBLE) {
			icon.setImageResource(R.drawable.ic_visibility_off);
		} else {
			icon.setImageResource(R.drawable.ic_visibility);
		}
	}

	@Override
	protected void grayOutItem(boolean gray) {
		super.grayOutItem(gray);
		float alpha = gray ? GREY_OUT : 1f;
		icon.setAlpha(alpha);
	}

}
