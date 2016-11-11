package org.briarproject.android.privategroup.memberlist;

import android.support.annotation.UiThread;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;

import org.briarproject.R;
import org.briarproject.android.view.AuthorView;
import org.briarproject.api.nullsafety.NotNullByDefault;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static org.briarproject.api.identity.Author.Status.OURSELVES;

@UiThread
@NotNullByDefault
class MemberListItemHolder extends RecyclerView.ViewHolder {

	private final AuthorView author;
	private final ImageView sharing;

	MemberListItemHolder(View v) {
		super(v);
		author = (AuthorView) v.findViewById(R.id.authorView);
		sharing = (ImageView) v.findViewById(R.id.sharingView);
	}

	protected void bind(MemberListItem item) {
		author.setAuthor(item.getMember());
		author.setAuthorStatus(item.getStatus());
		if (item.isSharing() && item.getStatus() != OURSELVES) {
			sharing.setVisibility(VISIBLE);
		} else {
			sharing.setVisibility(INVISIBLE);
		}
	}

}
