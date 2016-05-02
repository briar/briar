package org.briarproject.android.contact;

import android.content.Context;
import android.support.v7.util.SortedList;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.identity.Author;

import java.util.List;

import im.delight.android.identicons.IdenticonDrawable;

import static android.support.v7.util.SortedList.INVALID_POSITION;

public abstract class BaseContactListAdapter<VH extends BaseContactListAdapter.BaseContactHolder>
		extends RecyclerView.Adapter<VH> {

	protected final SortedList<ContactListItem> contacts;
	protected final OnItemClickListener listener;
	protected Context ctx;

	public BaseContactListAdapter(Context context, OnItemClickListener listener) {
		this.ctx = context;
		this.listener = listener;
		this.contacts = new SortedList<>(ContactListItem.class,
				new SortedListCallBacks());
	}

	@Override
	public void onBindViewHolder(final VH ui, final int position) {
		final ContactListItem item = getItem(position);

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
	}

	@Override
	public int getItemCount() {
		return contacts.size();
	}

	public ContactListItem getItem(int position) {
		if (position == INVALID_POSITION || contacts.size() <= position) {
			return null; // Not found
		}
		return contacts.get(position);
	}

	public void updateItem(int position, ContactListItem item) {
		contacts.updateItemAt(position, item);
	}

	public int findItemPosition(ContactListItem c) {
		return contacts.indexOf(c);
	}

	public int findItemPosition(ContactId c) {
		int count = getItemCount();
		for (int i = 0; i < count; i++) {
			ContactListItem item = getItem(i);
			if (item.getContact().getId().equals(c)) return i;
		}
		return INVALID_POSITION; // Not found
	}

	public void addAll(List<ContactListItem> contacts) {
		this.contacts.addAll(contacts);
	}

	public void add(ContactListItem contact) {
		contacts.add(contact);
	}

	public void remove(ContactListItem contact) {
		contacts.remove(contact);
	}

	public void clear() {
		contacts.clear();
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

	public int compareContactListItems(ContactListItem c1, ContactListItem c2) {
		return compareByName(c1, c2);
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

	protected class SortedListCallBacks extends SortedList.Callback<ContactListItem> {

		@Override
		public void onInserted(int position, int count) {
			notifyItemRangeInserted(position, count);
		}

		@Override
		public void onChanged(int position, int count) {
			notifyItemRangeChanged(position, count);
		}

		@Override
		public void onMoved(int fromPosition, int toPosition) {
			notifyItemMoved(fromPosition, toPosition);
		}

		@Override
		public void onRemoved(int position, int count) {
			notifyItemRangeRemoved(position, count);
		}

		@Override
		public int compare(ContactListItem c1, ContactListItem c2) {
			return compareContactListItems(c1, c2);
		}

		@Override
		public boolean areItemsTheSame(ContactListItem c1, ContactListItem c2) {
			return c1.getContact().getId().equals(c2.getContact().getId());
		}

		@Override
		public boolean areContentsTheSame(ContactListItem c1,
				ContactListItem c2) {
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
	}

	public interface OnItemClickListener {
		void onItemClick(View view, ContactListItem item);
	}

}
