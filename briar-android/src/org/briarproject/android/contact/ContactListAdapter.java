package org.briarproject.android.contact;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.support.v7.util.SortedList;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sync.GroupId;

import java.util.List;

import static android.support.v7.util.SortedList.INVALID_POSITION;

public class ContactListAdapter
		extends RecyclerView.Adapter<ContactListAdapter.ContactHolder> {

	private final SortedList<ContactListItem> contacts =
			new SortedList<ContactListItem>(ContactListItem.class,
					new SortedList.Callback<ContactListItem>() {
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
						public int compare(ContactListItem c1,
								ContactListItem c2) {
							// sort items by time
							// and do not take unread messages into account
							long time1 = c1.getTimestamp();
							long time2 = c2.getTimestamp();
							if (time1 < time2) return 1;
							if (time1 > time2) return -1;
							return 0;
						}

						@Override
						public boolean areItemsTheSame(ContactListItem c1,
								ContactListItem c2) {
							return c1.getContact().getId()
									.equals(c2.getContact().getId());
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
					});
	private Context ctx;

	public ContactListAdapter(Context context) {
		ctx = context;
	}

	@Override
	public ContactHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
		View v = LayoutInflater.from(viewGroup.getContext())
				.inflate(R.layout.list_item_contact, viewGroup, false);

		return new ContactHolder(v);
	}

	@Override
	public void onBindViewHolder(final ContactHolder ui, final int position) {
		final ContactListItem item = getItem(position);
		Resources res = ctx.getResources();

		int unread = item.getUnreadCount();
		if (unread > 0) {
			ui.layout.setBackgroundColor(
					res.getColor(R.color.unread_background));
		}

		if (item.isConnected()) {
			ui.bulb.setImageResource(R.drawable.contact_connected);
		} else {
			ui.bulb.setImageResource(R.drawable.contact_disconnected);
		}

		String contactName = item.getContact().getAuthor().getName();
		if (unread > 0) {
			ui.name.setText(contactName + " (" + unread + ")");
		} else {
			ui.name.setText(contactName);
		}

		if (item.isEmpty()) {
			ui.date.setText(R.string.no_private_messages);
		} else {
			long timestamp = item.getTimestamp();
			ui.date.setText(
					DateUtils.getRelativeTimeSpanString(ctx, timestamp));
		}

		ui.layout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				GroupId groupId = item.getGroupId();
				Intent i = new Intent(ctx, ConversationActivity.class);
				i.putExtra("briar.GROUP_ID", groupId.getBytes());
				ctx.startActivity(i);
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

	public int findItemPosition(ContactListItem item) {
		return contacts.indexOf(item);
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
		contacts.beginBatchedUpdates();

		while(contacts.size() != 0) {
			contacts.removeItemAt(0);
		}

		contacts.endBatchedUpdates();
	}

	public static class ContactHolder extends RecyclerView.ViewHolder {
		public ViewGroup layout;
		public ImageView bulb;
		public TextView name;
		public TextView date;

		public ContactHolder(View v) {
			super(v);

			layout = (ViewGroup) v;
			bulb = (ImageView) v.findViewById(R.id.bulbView);
			name = (TextView) v.findViewById(R.id.nameView);
			date = (TextView) v.findViewById(R.id.dateView);
		}
	}
}
