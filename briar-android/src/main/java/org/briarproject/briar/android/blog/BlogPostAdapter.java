package org.briarproject.briar.android.blog;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.util.BriarAdapter;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class BlogPostAdapter extends BriarAdapter<BlogPostItem, BlogPostViewHolder> {

	private final OnBlogPostClickListener listener;
	@Nullable
	private final FragmentManager fragmentManager;

	BlogPostAdapter(Context ctx, OnBlogPostClickListener listener,
			@Nullable FragmentManager fragmentManager) {
		super(ctx, BlogPostItem.class);
		this.listener = listener;
		this.fragmentManager = fragmentManager;
	}

	@Override
	public BlogPostViewHolder onCreateViewHolder(ViewGroup parent,
			int viewType) {
		View v = LayoutInflater.from(ctx).inflate(
				R.layout.list_item_blog_post, parent, false);
		return new BlogPostViewHolder(v, false, listener, fragmentManager);
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

}
