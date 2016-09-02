package org.briarproject.android.blogs;

import android.content.Context;
import android.support.v7.util.SortedList;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;

import java.util.Collection;

class BlogPostAdapter extends RecyclerView.Adapter<BlogPostViewHolder> {

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
	public BlogPostViewHolder onCreateViewHolder(ViewGroup parent,
			int viewType) {
		View v = LayoutInflater.from(ctx).inflate(
				R.layout.list_item_blog_post, parent, false);
		BlogPostViewHolder ui = new BlogPostViewHolder(v);
		ui.setOnBlogPostClickListener(listener);
		return ui;
	}

	@Override
	public void onBindViewHolder(BlogPostViewHolder ui, int position) {
		ui.bindItem(getItem(position));
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

	interface OnBlogPostClickListener {
		void onBlogPostClick(BlogPostItem post);
	}

}
