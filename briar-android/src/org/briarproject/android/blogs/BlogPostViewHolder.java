package org.briarproject.android.blogs;

import android.content.Context;
import android.content.Intent;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.blogs.BlogPostAdapter.OnBlogPostClickListener;
import org.briarproject.android.util.AuthorView;
import org.briarproject.api.blogs.BlogCommentHeader;
import org.briarproject.api.blogs.BlogPostHeader;
import org.briarproject.api.identity.Author;

import static android.support.v4.app.ActivityOptionsCompat.makeCustomAnimation;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static org.briarproject.android.BriarActivity.GROUP_ID;
import static org.briarproject.android.blogs.BlogActivity.POST_ID;
import static org.briarproject.api.blogs.MessageType.POST;

public class BlogPostViewHolder extends RecyclerView.ViewHolder {

	private final Context ctx;
	private OnBlogPostClickListener listener;

	private final ViewGroup layout;
	private final AuthorView reblogger;
	private final AuthorView author;
	private final ImageView reblogButton;
	private final TextView body;
	private final ViewGroup commentContainer;

	BlogPostViewHolder(View v) {
		super(v);

		ctx = v.getContext();
		layout = (ViewGroup) v;
		reblogger = (AuthorView) v.findViewById(R.id.rebloggerView);
		author = (AuthorView) v.findViewById(R.id.authorView);
		reblogButton = (ImageView) v.findViewById(R.id.commentView);
		body = (TextView) v.findViewById(R.id.bodyView);
		commentContainer =
				(ViewGroup) v.findViewById(R.id.commentContainer);
	}

	void setOnBlogPostClickListener(OnBlogPostClickListener listener) {
		this.listener = listener;
	}

	void setVisibility(int visibility) {
		layout.setVisibility(visibility);
	}

	void hideReblogButton() {
		reblogButton.setVisibility(GONE);
	}

	void bindItem(final BlogPostItem item) {
		// author and date
		BlogPostHeader post = item.getPostHeader();
		Author a = post.getAuthor();
		author.setAuthor(a);
		author.setAuthorStatus(post.getAuthorStatus());
		author.setDate(post.getTimestamp());
		// TODO make author clickable more often #624
		if (item.getHeader().getType() == POST) {
			author.setBlogLink(post.getGroupId());
		} else {
			author.unsetBlogLink();
		}

		// post body
		body.setText(item.getBody());

		// reblog button
		reblogButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent i = new Intent(ctx, ReblogActivity.class);
				i.putExtra(GROUP_ID, item.getGroupId().getBytes());
				i.putExtra(POST_ID, item.getId().getBytes());
				ActivityOptionsCompat options =
						makeCustomAnimation(ctx, android.R.anim.slide_in_left,
								android.R.anim.slide_out_right);
				Intent[] intents = { i };
				ContextCompat.startActivities(ctx, intents, options.toBundle());
			}
		});

		// comments
		commentContainer.removeAllViews();
		if (item instanceof BlogCommentItem) {
			onBindComment((BlogCommentItem) item);
		} else {
			reblogger.setVisibility(GONE);
		}

		layout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (listener != null) {
					listener.onBlogPostClick(item);
				}
			}
		});
	}

	private void onBindComment(final BlogCommentItem item) {
		// reblogger
		reblogger.setAuthor(item.getAuthor());
		reblogger.setAuthorStatus(item.getAuthorStatus());
		reblogger.setDate(item.getTimestamp());
		reblogger.setBlogLink(item.getGroupId());
		reblogger.setVisibility(VISIBLE);

		// comments
		for (BlogCommentHeader c : item.getComments()) {
			View v = LayoutInflater.from(ctx)
					.inflate(R.layout.list_item_blog_comment,
							commentContainer, false);

			AuthorView author = (AuthorView) v.findViewById(R.id.authorView);
			TextView body = (TextView) v.findViewById(R.id.bodyView);

			author.setAuthor(c.getAuthor());
			author.setAuthorStatus(c.getAuthorStatus());
			author.setDate(c.getTimestamp());
			// TODO make author clickable #624

			body.setText(c.getComment());

			commentContainer.addView(v);
		}
	}
}
