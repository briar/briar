package org.briarproject.briar.android.forum;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.util.BriarAdapter;
import org.briarproject.briar.android.util.UiUtils;
import org.briarproject.briar.android.view.TextAvatarView;
import org.briarproject.briar.api.forum.Forum;

import static android.support.v7.util.SortedList.INVALID_POSITION;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static org.briarproject.briar.android.activity.BriarActivity.GROUP_ID;
import static org.briarproject.briar.android.activity.BriarActivity.GROUP_NAME;

class ForumListAdapter
		extends BriarAdapter<ForumListItem, ForumListAdapter.ForumViewHolder> {

	ForumListAdapter(Context ctx) {
		super(ctx, ForumListItem.class);
	}

	@Override
	public ForumViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		View v = LayoutInflater.from(ctx).inflate(
				R.layout.list_item_forum, parent, false);
		return new ForumViewHolder(v);
	}

	@Override
	public void onBindViewHolder(ForumViewHolder ui, int position) {
		final ForumListItem item = getItemAt(position);
		if (item == null) return;

		// Avatar
		ui.avatar.setText(item.getForum().getName().substring(0, 1));
		ui.avatar.setBackgroundBytes(item.getForum().getId().getBytes());
		ui.avatar.setUnreadCount(item.getUnreadCount());

		// Forum Name
		ui.name.setText(item.getForum().getName());

		// Post Count
		int postCount = item.getPostCount();
		if (postCount > 0) {
			ui.avatar.setProblem(false);
			ui.postCount.setText(ctx.getResources()
					.getQuantityString(R.plurals.posts, postCount,
							postCount));
			ui.postCount.setTextColor(
					ContextCompat
							.getColor(ctx, R.color.briar_text_secondary));
		} else {
			ui.avatar.setProblem(true);
			ui.postCount.setText(ctx.getString(R.string.no_posts));
			ui.postCount.setTextColor(
					ContextCompat
							.getColor(ctx, R.color.briar_text_tertiary));
		}

		// Date
		if (item.isEmpty()) {
			ui.date.setVisibility(GONE);
		} else {
			long timestamp = item.getTimestamp();
			ui.date.setText(UiUtils.formatDate(ctx, timestamp));
			ui.date.setVisibility(VISIBLE);
		}

		// Open Forum on Click
		ui.layout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent i = new Intent(ctx, ForumActivity.class);
				Forum f = item.getForum();
				i.putExtra(GROUP_ID, f.getId().getBytes());
				i.putExtra(GROUP_NAME, f.getName());
				ctx.startActivity(i);
			}
		});
	}

	@Override
	public int compare(ForumListItem a, ForumListItem b) {
		if (a == b) return 0;
		// The forum with the newest message comes first
		long aTime = a.getTimestamp(), bTime = b.getTimestamp();
		if (aTime > bTime) return -1;
		if (aTime < bTime) return 1;
		// Break ties by forum name
		String aName = a.getForum().getName();
		String bName = b.getForum().getName();
		return String.CASE_INSENSITIVE_ORDER.compare(aName, bName);
	}

	@Override
	public boolean areContentsTheSame(ForumListItem a, ForumListItem b) {
		return a.isEmpty() == b.isEmpty() &&
				a.getTimestamp() == b.getTimestamp() &&
				a.getUnreadCount() == b.getUnreadCount();
	}

	@Override
	public boolean areItemsTheSame(ForumListItem a, ForumListItem b) {
		return a.getForum().equals(b.getForum());
	}

	int findItemPosition(GroupId g) {
		int count = getItemCount();
		for (int i = 0; i < count; i++) {
			ForumListItem item = getItemAt(i);
			if (item != null && item.getForum().getGroup().getId().equals(g))
				return i;
		}
		return INVALID_POSITION; // Not found
	}

	static class ForumViewHolder extends RecyclerView.ViewHolder {

		private final ViewGroup layout;
		private final TextAvatarView avatar;
		private final TextView name;
		private final TextView postCount;
		private final TextView date;

		private ForumViewHolder(View v) {
			super(v);

			layout = (ViewGroup) v;
			avatar = (TextAvatarView) v.findViewById(R.id.avatarView);
			name = (TextView) v.findViewById(R.id.forumNameView);
			postCount = (TextView) v.findViewById(R.id.postCountView);
			date = (TextView) v.findViewById(R.id.dateView);
		}
	}
}
