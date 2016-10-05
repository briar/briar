package org.briarproject.android.contact;

import android.content.Context;
import android.support.v4.view.ViewCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.util.AndroidUtils;
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

		ContactListItem item = getItemAt(position);
		if (item == null) return;

		// unread count
		long unread = item.getUnreadCount();
		if (unread > 0) {
			ui.unread.setText(String.valueOf(unread));
			ui.unread.setVisibility(View.VISIBLE);
		} else {
			ui.unread.setVisibility(View.INVISIBLE);
		}

		// date of last message
		if (item.isEmpty()) {
			ui.date.setText(R.string.date_no_private_messages);
		} else {
			long timestamp = item.getTimestamp();
			ui.date.setText(AndroidUtils.formatDate(ctx, timestamp));
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
		private final TextView unread;
		public final TextView date;
		public final TextView identity;

		private ContactHolder(View v) {
			super(v);

			bulb = (ImageView) v.findViewById(R.id.bulbView);
			unread = (TextView) v.findViewById(R.id.unreadCountView);
			date = (TextView) v.findViewById(R.id.dateView);
			identity = (TextView) v.findViewById(R.id.identityView);
		}
	}

	@Override
	public int compare(ContactListItem c1, ContactListItem c2) {
		return compareByTime(c1, c2);
	}
}
