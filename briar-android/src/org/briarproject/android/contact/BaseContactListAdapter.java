package org.briarproject.android.contact;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.util.BriarAdapter;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.identity.Author;
import org.briarproject.util.StringUtils;

import im.delight.android.identicons.IdenticonDrawable;

import static android.support.v7.util.SortedList.INVALID_POSITION;

public abstract class BaseContactListAdapter<VH extends BaseContactListAdapter.BaseContactHolder>
		extends BriarAdapter<ContactListItem, VH> {

	@Nullable
	protected final OnItemClickListener listener;

	public BaseContactListAdapter(Context ctx,
			@Nullable OnItemClickListener listener) {
		super(ctx, ContactListItem.class);
		this.listener = listener;
	}

	@Override
	public void onBindViewHolder(final VH ui, int position) {
		final ContactListItem item = getItemAt(position);
		if (item == null) return;

		Author author = item.getContact().getAuthor();
		ui.avatar.setImageDrawable(
				new IdenticonDrawable(author.getId().getBytes()));
		String contactName = author.getName();
		ui.name.setText(contactName);

		ui.layout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (listener != null) listener.onItemClick(ui.avatar, item);
			}
		});

		ViewCompat.setTransitionName(ui.avatar, "avatar" +
				StringUtils.toHexString(item.getGroupId().getBytes()));
	}

	@Override
	public int compare(ContactListItem c1, ContactListItem c2) {
		return compareByName(c1, c2);
	}

	@Override
	public boolean areItemsTheSame(ContactListItem c1, ContactListItem c2) {
		return c1.getContact().getId().equals(c2.getContact().getId());
	}

	@Override
	public boolean areContentsTheSame(ContactListItem c1, ContactListItem c2) {
		// check for all properties that influence visual
		// representation of contact
		if (c1.isConnected() != c2.isConnected()) {
			return false;
		}
		if (c1.getUnreadCount() != c2.getUnreadCount()) {
			return false;
		}
		if (c1.getTimestamp() != c2.getTimestamp()) {
			return false;
		}
		return true;
	}

	int findItemPosition(ContactId c) {
		int count = getItemCount();
		for (int i = 0; i < count; i++) {
			ContactListItem item = getItemAt(i);
			if (item != null && item.getContact().getId().equals(c))
				return i;
		}
		return INVALID_POSITION; // Not found
	}

	public static class BaseContactHolder extends RecyclerView.ViewHolder {

		public final ViewGroup layout;
		public final ImageView avatar;
		public final TextView name;

		public BaseContactHolder(View v) {
			super(v);

			layout = (ViewGroup) v;
			avatar = (ImageView) v.findViewById(R.id.avatarView);
			name = (TextView) v.findViewById(R.id.nameView);
		}
	}

	protected int compareByName(ContactListItem c1, ContactListItem c2) {
		int authorCompare = c1.getLocalAuthor().getName()
				.compareTo(c2.getLocalAuthor().getName());
		if (authorCompare == 0) {
			// if names are equal, compare by time instead
			return compareByTime(c1, c2);
		} else {
			return authorCompare;
		}
	}

	protected int compareByTime(ContactListItem c1, ContactListItem c2) {
		long time1 = c1.getTimestamp();
		long time2 = c2.getTimestamp();
		if (time1 < time2) return 1;
		if (time1 > time2) return -1;
		return 0;
	}

	public interface OnItemClickListener {
		void onItemClick(View view, ContactListItem item);
	}

}
