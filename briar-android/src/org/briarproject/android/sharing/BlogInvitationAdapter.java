package org.briarproject.android.sharing;

import android.content.Context;

import org.briarproject.R;
import org.briarproject.api.blogs.Blog;
import org.briarproject.api.sharing.InvitationItem;

class BlogInvitationAdapter extends InvitationAdapter {

	BlogInvitationAdapter(Context ctx, AvailableForumClickListener listener) {
		super(ctx, listener);
	}

	@Override
	public void onBindViewHolder(InvitationsViewHolder ui, int position) {
		super.onBindViewHolder(ui, position);
		InvitationItem item = getItem(position);
		Blog blog = (Blog) item.getShareable();

		ui.avatar.setAuthorAvatar(blog.getAuthor());

		ui.name.setText(ctx.getString(R.string.blogs_personal_blog,
				blog.getAuthor().getName()));

		if (item.isSubscribed()) {
			ui.subscribed.setText(ctx.getString(R.string.blogs_sharing_exists,
					blog.getAuthor().getName()));
		}
	}

	int compareInvitations(InvitationItem o1, InvitationItem o2) {
		return String.CASE_INSENSITIVE_ORDER
				.compare(((Blog) o1.getShareable()).getAuthor().getName(),
						((Blog) o2.getShareable()).getAuthor().getName());
	}

}
