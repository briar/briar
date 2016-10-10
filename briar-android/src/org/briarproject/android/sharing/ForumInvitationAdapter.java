package org.briarproject.android.sharing;

import android.content.Context;

import org.briarproject.api.forum.Forum;
import org.briarproject.api.sharing.InvitationItem;

class ForumInvitationAdapter extends InvitationAdapter {

	ForumInvitationAdapter(Context ctx, AvailableForumClickListener listener) {
		super(ctx, listener);
	}

	@Override
	public void onBindViewHolder(InvitationsViewHolder ui, int position) {
		super.onBindViewHolder(ui, position);
		InvitationItem item = getItemAt(position);
		if (item == null) return;

		Forum forum = (Forum) item.getShareable();

		ui.avatar.setText(forum.getName().substring(0, 1));
		ui.avatar.setBackgroundBytes(item.getShareable().getId().getBytes());

		ui.name.setText(forum.getName());
	}

	@Override
	public int compare(InvitationItem o1, InvitationItem o2) {
		return String.CASE_INSENSITIVE_ORDER
				.compare(((Forum) o1.getShareable()).getName(),
						((Forum) o2.getShareable()).getName());
	}

}
