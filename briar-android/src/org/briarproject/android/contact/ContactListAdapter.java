package org.briarproject.android.contact;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.util.StringUtils;

public class ContactListAdapter
		extends BaseContactListAdapter<ContactListAdapter.ContactHolder> {

	public ContactListAdapter(Context context, OnItemClickListener listener) {
		super(context, listener);
	}

	@Override
	public ContactHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
		View v = LayoutInflater.from(viewGroup.getContext()).inflate(
				R.layout.list_item_contact, viewGroup, false);

		return new ContactHolder(v);
	}

	@Override
	public void onBindViewHolder(ContactHolder ui, int position) {
		super.onBindViewHolder(ui, position);

		ContactListItem item = getItem(position);

		// unread count
		int unread = item.getUnreadCount();
		if (unread > 0) {
			ui.unread.setText(String.valueOf(unread));
			ui.unread.setVisibility(View.VISIBLE);
		} else {
			ui.unread.setVisibility(View.INVISIBLE);
		}

		// date of last message
		if (item.isEmpty()) {
			ui.date.setText(R.string.no_private_messages);
		} else {
			// TODO show this as X units ago
			long timestamp = item.getTimestamp();
			ui.date.setText(
					DateUtils.getRelativeTimeSpanString(ctx, timestamp));
		}

		// online/offline
		if (item.isConnected()) {
			ui.bulb.setImageResource(R.drawable.contact_connected);
		} else {
			ui.bulb.setImageResource(R.drawable.contact_disconnected);
		}

		ViewCompat.setTransitionName(ui.bulb,
				"bulb" + StringUtils.toHexString(item.getGroupId().getBytes()));
	}

	protected static class ContactHolder
			extends BaseContactListAdapter.BaseContactHolder {

		public final ImageView bulb;
		public final TextView unread;
		public final TextView date;
		public final TextView identity;

		public ContactHolder(View v) {
			super(v);

			bulb = (ImageView) v.findViewById(R.id.bulbView);
			unread = (TextView) v.findViewById(R.id.unreadCountView);
			date = (TextView) v.findViewById(R.id.dateView);
			identity = (TextView) v.findViewById(R.id.identityView);
		}
	}

	@Override
	public int compareContactListItems(ContactListItem c1, ContactListItem c2) {
		return compareByTime(c1, c2);
	}
}
