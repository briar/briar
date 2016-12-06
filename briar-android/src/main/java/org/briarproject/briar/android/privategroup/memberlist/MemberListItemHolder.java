package org.briarproject.briar.android.privategroup.memberlist;

import android.support.annotation.UiThread;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.view.AuthorView;

import static org.briarproject.bramble.api.identity.Author.Status.OURSELVES;
import static org.briarproject.bramble.api.identity.Author.Status.UNKNOWN;
import static org.briarproject.briar.android.privategroup.VisibilityHelper.getVisibilityIcon;
import static org.briarproject.briar.android.privategroup.VisibilityHelper.getVisibilityString;

@UiThread
@NotNullByDefault
class MemberListItemHolder extends RecyclerView.ViewHolder {

	private final AuthorView author;
	private final ImageView bulb;
	private final TextView creator;
	private final ImageView icon;
	private final TextView info;

	MemberListItemHolder(View v) {
		super(v);
		author = (AuthorView) v.findViewById(R.id.authorView);
		bulb = (ImageView) v.findViewById(R.id.bulbView);
		creator = (TextView) v.findViewById(R.id.creatorView);
		icon = (ImageView) v.findViewById(R.id.icon);
		info = (TextView) v.findViewById(R.id.info);
	}

	protected void bind(MemberListItem item) {
		// member name, avatar and status
		author.setAuthor(item.getMember());
		author.setAuthorStatus(item.getStatus());

		// online status of visible contacts
		if (item.getContactId() != null) {
			bulb.setVisibility(View.VISIBLE);
			if (item.isOnline()) {
				bulb.setImageResource(R.drawable.contact_connected);
			} else {
				bulb.setImageResource(R.drawable.contact_disconnected);
			}
		} else {
			bulb.setVisibility(View.GONE);
		}

		// text shown for creator
		if (item.isCreator()) {
			creator.setVisibility(View.VISIBLE);
			if (item.getStatus() == OURSELVES) {
				creator.setText(R.string.groups_member_created_you);
			} else {
				creator.setText(creator.getContext()
						.getString(R.string.groups_member_created,
								item.getMember().getName()));
			}
		} else {
			creator.setVisibility(View.GONE);
		}

		// visibility information
		if (item.getStatus() == OURSELVES || item.getStatus() == UNKNOWN) {
			icon.setVisibility(View.GONE);
			info.setVisibility(View.GONE);
		} else {
			icon.setVisibility(View.VISIBLE);
			icon.setImageResource(getVisibilityIcon(item.getVisibility()));
			info.setVisibility(View.VISIBLE);
			info.setText(
					getVisibilityString(info.getContext(), item.getVisibility(),
							item.getMember().getName()));
		}
	}

}
