package org.briarproject.android.contact;

import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.support.v4.content.ContextCompat;
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
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;

import java.util.List;

import im.delight.android.identicons.IdenticonDrawable;

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
							int authorCompare = 0;
							if (chooser) {
								authorCompare = c1.getLocalAuthor().getName()
										.compareTo(
												c2.getLocalAuthor().getName());
							}
							if (authorCompare == 0) {
								// sort items by time
								// and do not take unread messages into account
								long time1 = c1.getTimestamp();
								long time2 = c2.getTimestamp();
								if (time1 < time2) return 1;
								if (time1 > time2) return -1;
								return 0;
							} else {
								return authorCompare;
							}
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
	private final OnItemClickListener listener;
	private final boolean chooser;
	private Context ctx;
	private AuthorId localAuthorId;

	public ContactListAdapter(Context context, OnItemClickListener listener,
			boolean chooser) {
		ctx = context;
		this.listener = listener;
		this.chooser = chooser;
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

		int unread = item.getUnreadCount();
		if (!chooser && unread > 0) {
			ui.layout.setBackgroundColor(
					ContextCompat.getColor(ctx, R.color.unread_background));
		}

		if (item.isConnected()) {
			ui.bulb.setImageResource(R.drawable.contact_connected);
		} else {
			ui.bulb.setImageResource(R.drawable.contact_disconnected);
		}

		Author author = item.getContact().getAuthor();
		ui.avatar.setImageDrawable(
				new IdenticonDrawable(author.getId().getBytes()));
		String contactName = author.getName();

		if (!chooser && unread > 0) {
			// TODO show these in a bubble on top of the avatar
			ui.name.setText(contactName + " (" + unread + ")");
		} else {
			ui.name.setText(contactName);
		}

		if (chooser) {
			ui.identity.setText(item.getLocalAuthor().getName());
		} else {
			ui.identity.setVisibility(View.GONE);
		}

		if (item.isEmpty()) {
			ui.date.setText(R.string.no_private_messages);
		} else {
			// TODO show this as X units ago
			long timestamp = item.getTimestamp();
			ui.date.setText(
					DateUtils.getRelativeTimeSpanString(ctx, timestamp));
		}

		if (chooser && !item.getLocalAuthor().getId().equals(localAuthorId)) {
			grayOutItem(ui);
		}

		ui.layout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				listener.onItemClick(ui.avatar, item);
			}
		});
	}

	@Override
	public int getItemCount() {
		return contacts.size();
	}

	/**
	 * Set the identity from whose perspective the contact shall be chosen.
	 * This is only used if chooser is true.
	 * @param authorId The ID of the local Author
	 */
	public void setLocalAuthor(AuthorId authorId) {
		localAuthorId = authorId;
		notifyDataSetChanged();
	}

	private void grayOutItem(final ContactHolder ui) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			float alpha = 0.25f;
			ui.bulb.setAlpha(alpha);
			ui.avatar.setAlpha(alpha);
			ui.name.setAlpha(alpha);
			ui.date.setAlpha(alpha);
			ui.identity.setAlpha(alpha);
		} else {
			ColorFilter colorFilter = new PorterDuffColorFilter(Color.GRAY,
					PorterDuff.Mode.MULTIPLY);
			ui.bulb.setColorFilter(colorFilter);
			ui.avatar.setColorFilter(colorFilter);
			ui.name.setEnabled(false);
			ui.date.setEnabled(false);
		}
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
		public ImageView avatar;
		public TextView name;
		public TextView identity;
		public TextView date;

		public ContactHolder(View v) {
			super(v);

			layout = (ViewGroup) v;
			bulb = (ImageView) v.findViewById(R.id.bulbView);
			avatar = (ImageView) v.findViewById(R.id.avatarView);
			name = (TextView) v.findViewById(R.id.nameView);
			identity = (TextView) v.findViewById(R.id.identityView);
			date = (TextView) v.findViewById(R.id.dateView);
		}
	}

	public interface OnItemClickListener {
		void onItemClick(View view, ContactListItem item);
	}

}
