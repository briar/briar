package org.briarproject.briar.android.contact;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.View;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.briar.android.util.BriarAdapter;

import javax.annotation.Nullable;

import static android.support.v7.util.SortedList.INVALID_POSITION;
import static org.briarproject.briar.android.util.UiUtils.getContactDisplayName;

public abstract class BaseContactListAdapter<I extends ContactItem, VH extends ContactItemViewHolder<I>>
		extends BriarAdapter<I, VH> {

	@Nullable
	protected final OnContactClickListener<I> listener;

	public BaseContactListAdapter(Context ctx, Class<I> c,
			@Nullable OnContactClickListener<I> listener) {
		super(ctx, c);
		this.listener = listener;
	}

	@Override
	public void onBindViewHolder(@NonNull VH ui, int position) {
		I item = items.get(position);
		ui.bind(item, listener);
	}

	@Override
	public int compare(I c1, I c2) {
		return getContactDisplayName(c1.getContact())
				.compareTo(getContactDisplayName(c2.getContact()));
	}

	@Override
	public boolean areItemsTheSame(I c1, I c2) {
		return c1.getContact().getId().equals(c2.getContact().getId());
	}

	@Override
	public boolean areContentsTheSame(ContactItem c1, ContactItem c2) {
		return true;
	}

	int findItemPosition(ContactId c) {
		int count = getItemCount();
		for (int i = 0; i < count; i++) {
			I item = getItemAt(i);
			if (item != null && item.getContact().getId().equals(c))
				return i;
		}
		return INVALID_POSITION; // Not found
	}

	public interface OnContactClickListener<I> {
		void onItemClick(View view, I item);
	}

}
