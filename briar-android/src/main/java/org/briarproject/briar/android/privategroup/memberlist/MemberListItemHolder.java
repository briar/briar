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
	private final ImageView icon;
	private final TextView info;

	MemberListItemHolder(View v) {
		super(v);
		author = (AuthorView) v.findViewById(R.id.authorView);
		icon = (ImageView) v.findViewById(R.id.icon);
		info = (TextView) v.findViewById(R.id.info);
	}

	protected void bind(MemberListItem item) {
		author.setAuthor(item.getMember());
		author.setAuthorStatus(item.getStatus());
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
