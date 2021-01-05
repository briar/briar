package org.briarproject.briar.android.forum;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.briarproject.briar.R;
import org.briarproject.briar.android.util.UiUtils;
import org.briarproject.briar.android.view.TextAvatarView;
import org.briarproject.briar.api.forum.Forum;

import androidx.recyclerview.widget.RecyclerView;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static org.briarproject.briar.android.activity.BriarActivity.GROUP_ID;
import static org.briarproject.briar.android.activity.BriarActivity.GROUP_NAME;

class ForumViewHolder extends RecyclerView.ViewHolder {

	private final Context ctx;
	private final ViewGroup layout;
	private final TextAvatarView avatar;
	private final TextView name;
	private final TextView postCount;
	private final TextView date;

	ForumViewHolder(View v) {
		super(v);
		ctx = v.getContext();
		layout = (ViewGroup) v;
		avatar = v.findViewById(R.id.avatarView);
		name = v.findViewById(R.id.forumNameView);
		postCount = v.findViewById(R.id.postCountView);
		date = v.findViewById(R.id.dateView);
	}

	void bind(ForumListItem item) {
		// Avatar
		avatar.setText(item.getForum().getName().substring(0, 1));
		avatar.setBackgroundBytes(item.getForum().getId().getBytes());
		avatar.setUnreadCount(item.getUnreadCount());

		// Forum Name
		name.setText(item.getForum().getName());

		// Post Count
		int count = item.getPostCount();
		if (count > 0) {
			postCount.setText(ctx.getResources()
					.getQuantityString(R.plurals.posts, count, count));
		} else {
			postCount.setText(ctx.getString(R.string.no_posts));
		}

		// Date
		if (item.isEmpty()) {
			date.setVisibility(GONE);
		} else {
			long timestamp = item.getTimestamp();
			date.setText(UiUtils.formatDate(ctx, timestamp));
			date.setVisibility(VISIBLE);
		}

		// Open Forum on Click
		layout.setOnClickListener(v -> {
			Intent i = new Intent(ctx, ForumActivity.class);
			Forum f = item.getForum();
			i.putExtra(GROUP_ID, f.getId().getBytes());
			i.putExtra(GROUP_NAME, f.getName());
			ctx.startActivity(i);
		});
	}

}
