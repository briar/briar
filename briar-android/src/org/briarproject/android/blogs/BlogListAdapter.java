package org.briarproject.android.blogs;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.util.SortedList;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.util.TextAvatarView;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

class BlogListAdapter extends
		RecyclerView.Adapter<BlogListAdapter.BlogViewHolder> {

	private SortedList<BlogListItem> blogs = new SortedList<>(
			BlogListItem.class, new SortedList.Callback<BlogListItem>() {

		@Override
		public int compare(BlogListItem a, BlogListItem b) {
			if (a == b) return 0;
			// The blog with the newest message comes first
			long aTime = a.getTimestamp(), bTime = b.getTimestamp();
			if (aTime > bTime) return -1;
			if (aTime < bTime) return 1;
			// Break ties by blog name
			String aName = a.getName();
			String bName = b.getName();
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
		public boolean areContentsTheSame(BlogListItem a, BlogListItem b) {
			return a.getBlog().equals(b.getBlog()) &&
					a.getTimestamp() == b.getTimestamp() &&
					a.getUnreadCount() == b.getUnreadCount();
		}

		@Override
		public boolean areItemsTheSame(BlogListItem a, BlogListItem b) {
			return a.getBlog().equals(b.getBlog());
		}
	});

	private final Context ctx;

	BlogListAdapter(Context ctx) {
		this.ctx = ctx;
	}

	@Override
	public BlogViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		View v = LayoutInflater.from(ctx).inflate(
				R.layout.list_item_blog, parent, false);
		return new BlogViewHolder(v);
	}

	@Override
	public void onBindViewHolder(BlogViewHolder ui, int position) {
		final BlogListItem item = getItem(position);

		// Avatar
		ui.avatar.setText(item.getName().substring(0, 1));
		ui.avatar.setBackgroundBytes(item.getBlog().getId().getBytes());
		ui.avatar.setUnreadCount(item.getUnreadCount());

		// Blog Name
		ui.name.setText(item.getName());

		// Post Count
		int postCount = item.getPostCount();
		ui.postCount.setText(ctx.getResources()
				.getQuantityString(R.plurals.posts, postCount, postCount));
		ui.postCount.setTextColor(
				ContextCompat.getColor(ctx, R.color.briar_text_secondary));

		// Date and Status
		if (item.isEmpty()) {
			ui.date.setVisibility(GONE);
			ui.avatar.setProblem(true);
			ui.status.setText(ctx.getString(R.string.blogs_blog_is_empty));
			ui.status.setVisibility(VISIBLE);
		} else {
			long timestamp = item.getTimestamp();
			ui.date.setText(
					DateUtils.getRelativeTimeSpanString(ctx, timestamp));
			ui.date.setVisibility(VISIBLE);
			ui.status.setVisibility(GONE);
		}

		// Open Blog on Click
		ui.layout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// TODO #415
/*				Intent i = new Intent(ctx, BlogActivity.class);
				Blog b = item.getBlog();
				i.putExtra(GROUP_ID, b.getId().getBytes());
				i.putExtra(BLOG_NAME, b.getName());
				ctx.startActivity(i);
*/			}
		});
	}

	@Override
	public int getItemCount() {
		return blogs.size();
	}

	public BlogListItem getItem(int position) {
		return blogs.get(position);
	}

	@Nullable
	public BlogListItem getItem(GroupId g) {
		for (int i = 0; i < blogs.size(); i++) {
			BlogListItem item = blogs.get(i);
			if (item.getBlog().getGroup().getId().equals(g)) {
				return item;
			}
		}
		return null;
	}

	public void addAll(Collection<BlogListItem> items) {
		blogs.addAll(items);
	}

	void updateItem(BlogListItem item) {
		BlogListItem oldItem = getItem(item.getBlog().getGroup().getId());
		int position = blogs.indexOf(oldItem);
		blogs.updateItemAt(position, item);
	}

	public void remove(BlogListItem item) {
		blogs.remove(item);
	}

	public void clear() {
		blogs.clear();
	}

	public boolean isEmpty() {
		return blogs.size() == 0;
	}

	static class BlogViewHolder extends RecyclerView.ViewHolder {

		private final ViewGroup layout;
		private final TextAvatarView avatar;
		private final TextView name;
		private final TextView postCount;
		private final TextView date;
		private final TextView status;

		BlogViewHolder(View v) {
			super(v);

			layout = (ViewGroup) v;
			avatar = (TextAvatarView) v.findViewById(R.id.avatarView);
			name = (TextView) v.findViewById(R.id.nameView);
			postCount = (TextView) v.findViewById(R.id.postCountView);
			date = (TextView) v.findViewById(R.id.dateView);
			status = (TextView) v.findViewById(R.id.statusView);
		}
	}
}
