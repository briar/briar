package org.briarproject.briar.android.contactselection;

import android.support.annotation.UiThread;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.contact.BaseContactListAdapter.OnContactClickListener;
import org.briarproject.briar.android.contact.ContactItemViewHolder;

import javax.annotation.Nullable;

import static org.briarproject.briar.android.util.UiUtils.GREY_OUT;

@UiThread
@NotNullByDefault
public class BaseSelectableContactHolder<I extends SelectableContactItem>
		extends ContactItemViewHolder<I> {

	private final CheckBox checkBox;
	protected final TextView info;

	public BaseSelectableContactHolder(View v) {
		super(v);
		checkBox = (CheckBox) v.findViewById(R.id.checkBox);
		info = (TextView) v.findViewById(R.id.infoView);
	}

	@Override
	protected void bind(I item, @Nullable
			OnContactClickListener<I> listener) {
		super.bind(item, listener);

		if (item.isSelected()) {
			checkBox.setChecked(true);
		} else {
			checkBox.setChecked(false);
		}

		if (item.isDisabled()) {
			layout.setEnabled(false);
			grayOutItem(true);
		} else {
			layout.setEnabled(true);
			grayOutItem(false);
		}
	}

	protected void grayOutItem(boolean gray) {
		float alpha = gray ? GREY_OUT : 1f;
		avatar.setAlpha(alpha);
		name.setAlpha(alpha);
		checkBox.setAlpha(alpha);
		info.setAlpha(alpha);
	}

}
