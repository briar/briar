package org.briarproject.briar.android.blog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.briar.R;
import org.briarproject.briar.android.util.BriarAdapter;

class BlogPostAdapter
		extends BriarAdapter<BlogPostItem, BlogPostViewHolder> {

	private final OnBlogPostClickListener listener;

	BlogPostAdapter(Context ctx, OnBlogPostClickListener listener) {
		super(ctx, BlogPostItem.class);
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
		ui.bindItem(getItemAt(position));
	}

	@Override
	public int compare(BlogPostItem a, BlogPostItem b) {
		return a.compareTo(b);
	}

	@Override
	public boolean areContentsTheSame(BlogPostItem a, BlogPostItem b) {
		return a.isRead() == b.isRead();
	}

	@Override
	public boolean areItemsTheSame(BlogPostItem a, BlogPostItem b) {
		return a.getId().equals(b.getId());
	}

	interface OnBlogPostClickListener {
		void onBlogPostClick(BlogPostItem post);
	}

}
