package org.briarproject.android.forum;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.util.SortedList;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.util.AndroidUtils;
import org.briarproject.android.view.TextAvatarView;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static org.briarproject.android.BriarActivity.GROUP_ID;
import static org.briarproject.android.forum.ForumActivity.FORUM_NAME;

class ForumListAdapter extends
		RecyclerView.Adapter<ForumListAdapter.ForumViewHolder> {

	private SortedList<ForumListItem> forums = new SortedList<>(
			ForumListItem.class, new SortedList.Callback<ForumListItem>() {

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
		public void onInserted(int position, int count) {
			notifyItemRangeInserted(position, count);
		}

		@Override
		public void onRemoved(int position, int count) {
			notifyItemRangeRemoved(position, count);
		}

		@Override
		public void onMoved(int fromPosition, int toPosition) {
			notifyItemMoved(fromPosition, toPosition);
		}

		@Override
		public void onChanged(int position, int count) {
			notifyItemRangeChanged(position, count);
		}

		@Override
		public boolean areContentsTheSame(ForumListItem a, ForumListItem b) {
			return a.getForum().equals(b.getForum()) &&
					a.getTimestamp() == b.getTimestamp() &&
					a.getUnreadCount() == b.getUnreadCount();
		}

		@Override
		public boolean areItemsTheSame(ForumListItem a, ForumListItem b) {
			return a.getForum().equals(b.getForum());
		}
	});

	private final Context ctx;

	ForumListAdapter(Context ctx) {
		this.ctx = ctx;
	}

	@Override
	public ForumViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		View v = LayoutInflater.from(ctx).inflate(
				R.layout.list_item_forum, parent, false);
		return new ForumViewHolder(v);
	}

	@Override
	public void onBindViewHolder(ForumViewHolder ui, int position) {
		final ForumListItem item = getItem(position);

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
			ui.date.setText(AndroidUtils.formatDate(ctx, timestamp));
			ui.date.setVisibility(VISIBLE);
		}

		// Open Forum on Click
		ui.layout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent i = new Intent(ctx, ForumActivity.class);
				Forum f = item.getForum();
				i.putExtra(GROUP_ID, f.getId().getBytes());
				i.putExtra(FORUM_NAME, f.getName());
				ctx.startActivity(i);
			}
		});
	}

	@Override
	public int getItemCount() {
		return forums.size();
	}

	public ForumListItem getItem(int position) {
		return forums.get(position);
	}

	@Nullable
	public ForumListItem getItem(GroupId g) {
		for (int i = 0; i < forums.size(); i++) {
			ForumListItem item = forums.get(i);
			if (item.getForum().getGroup().getId().equals(g)) {
				return item;
			}
		}
		return null;
	}

	public void addAll(Collection<ForumListItem> items) {
		forums.addAll(items);
	}

	void updateItem(ForumListItem item) {
		ForumListItem oldItem = getItem(item.getForum().getGroup().getId());
		int position = forums.indexOf(oldItem);
		forums.updateItemAt(position, item);
	}

	public void remove(ForumListItem item) {
		forums.remove(item);
	}

	public void clear() {
		forums.clear();
	}

	public boolean isEmpty() {
		return forums.size() == 0;
	}

	static class ForumViewHolder extends RecyclerView.ViewHolder {

		private final ViewGroup layout;
		private final TextAvatarView avatar;
		private final TextView name;
		private final TextView postCount;
		private final TextView date;

		ForumViewHolder(View v) {
			super(v);

			layout = (ViewGroup) v;
			avatar = (TextAvatarView) v.findViewById(R.id.avatarView);
			name = (TextView) v.findViewById(R.id.forumNameView);
			postCount = (TextView) v.findViewById(R.id.postCountView);
			date = (TextView) v.findViewById(R.id.dateView);
		}
	}
}
