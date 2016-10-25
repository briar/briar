package org.briarproject.android.contact;

import android.support.annotation.UiThread;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.contact.BaseContactListAdapter.OnContactClickListener;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.GroupId;
import org.jetbrains.annotations.Nullable;

import static android.support.v4.view.ViewCompat.setTransitionName;
import static org.briarproject.android.util.AndroidUtils.formatDate;
import static org.briarproject.util.StringUtils.toHexString;

@UiThread
@NotNullByDefault
class ContactListItemViewHolder extends ContactItemViewHolder<ContactListItem> {

	protected final ImageView bulb;
	private final TextView unread;
	private final TextView date;

	public ContactListItemViewHolder(View v) {
		super(v);
		bulb = (ImageView) v.findViewById(R.id.bulbView);
		unread = (TextView) v.findViewById(R.id.unreadCountView);
		date = (TextView) v.findViewById(R.id.dateView);
	}

	@Override
	protected void bind(ContactListItem item, @Nullable
			OnContactClickListener<ContactListItem> listener) {
		super.bind(item, listener);

		// unread count
		int unreadCount = item.getUnreadCount();
		if (unreadCount > 0) {
			unread.setText(String.valueOf(unreadCount));
			unread.setVisibility(View.VISIBLE);
		} else {
			unread.setVisibility(View.INVISIBLE);
		}

		// date of last message
		if (item.isEmpty()) {
			date.setText(R.string.date_no_private_messages);
		} else {
			long timestamp = item.getTimestamp();
			date.setText(formatDate(date.getContext(), timestamp));
		}

		// online/offline
		if (item.isConnected()) {
			bulb.setImageResource(R.drawable.contact_connected);
		} else {
			bulb.setImageResource(R.drawable.contact_disconnected);
		}

		setTransitionName(avatar, getAvatarTransitionName(item.getGroupId()));
		setTransitionName(bulb, getBulbTransitionName(item.getGroupId()));
	}

	private String getAvatarTransitionName(GroupId g) {
		return "avatar" + toHexString(g.getBytes());
	}

	private String getBulbTransitionName(GroupId g) {
		return "bulb" + toHexString(g.getBytes());
	}

}
