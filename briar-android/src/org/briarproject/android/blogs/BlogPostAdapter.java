package org.briarproject.android.blogs;

import android.content.Context;
import android.support.v7.util.SortedList;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.util.AndroidUtils;
import org.briarproject.android.util.TrustIndicatorView;
import org.briarproject.api.identity.Author;
import org.briarproject.util.StringUtils;

import java.util.Collection;

import de.hdodenhof.circleimageview.CircleImageView;
import im.delight.android.identicons.IdenticonDrawable;

class BlogPostAdapter extends
		RecyclerView.Adapter<BlogPostAdapter.BlogPostHolder> {

	private SortedList<BlogPostItem> posts = new SortedList<>(
			BlogPostItem.class, new SortedList.Callback<BlogPostItem>() {

		@Override
		public int compare(BlogPostItem a, BlogPostItem b) {
			return a.compareTo(b);
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
		public boolean areContentsTheSame(BlogPostItem a, BlogPostItem b) {
			return a.isRead() == b.isRead();
		}

		@Override
		public boolean areItemsTheSame(BlogPostItem a, BlogPostItem b) {
			return a.getId().equals(b.getId());
		}
	});

	private final Context ctx;
	private final OnBlogPostClickListener listener;

	BlogPostAdapter(Context ctx, OnBlogPostClickListener listener) {
		this.ctx = ctx;
		this.listener = listener;
	}

	@Override
	public BlogPostHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		View v = LayoutInflater.from(ctx).inflate(
				R.layout.list_item_blog_post, parent, false);
		return new BlogPostHolder(v);
	}

	@Override
	public void onBindViewHolder(final BlogPostHolder ui, int position) {
		final BlogPostItem post = getItem(position);

		Author author = post.getAuthor();
		IdenticonDrawable d = new IdenticonDrawable(author.getId().getBytes());
		ui.avatar.setImageDrawable(d);
		ui.author.setText(author.getName());
		ui.trust.setTrustLevel(post.getAuthorStatus());

		// date
		ui.date.setText(AndroidUtils.formatDate(ctx, post.getTimestamp()));

		// post body
		ui.body.setText(StringUtils.fromUtf8(post.getBody()));

		ui.layout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				listener.onBlogPostClick(post);
			}
		});
	}

	@Override
	public int getItemCount() {
		return posts.size();
	}

	public BlogPostItem getItem(int position) {
		return posts.get(position);
	}

	public void add(BlogPostItem item) {
		posts.add(item);
	}

	public void addAll(Collection<BlogPostItem> items) {
		posts.addAll(items);
	}

	public void remove(BlogPostItem item) {
		posts.remove(item);
	}

	public void clear() {
		posts.clear();
	}

	public boolean isEmpty() {
		return posts.size() == 0;
	}

	static class BlogPostHolder extends RecyclerView.ViewHolder {

		private final ViewGroup layout;
		private final CircleImageView avatar;
		private final TextView author;
		private final TrustIndicatorView trust;
		private final TextView date;
		private final TextView body;

		BlogPostHolder(View v) {
			super(v);

			layout = (ViewGroup) v;
			avatar = (CircleImageView) v.findViewById(R.id.avatar);
			author = (TextView) v.findViewById(R.id.authorName);
			trust = (TrustIndicatorView) v.findViewById(R.id.trustIndicator);
			date = (TextView) v.findViewById(R.id.dateView);
			body = (TextView) v.findViewById(R.id.bodyView);
		}
	}

	interface OnBlogPostClickListener {
		void onBlogPostClick(BlogPostItem post);
	}

}
