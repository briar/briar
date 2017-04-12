package org.briarproject.briar.android.blog;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.UiThread;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.blog.BlogPostAdapter.OnBlogPostClickListener;
import org.briarproject.briar.android.view.AuthorView;
import org.briarproject.briar.api.blog.BlogCommentHeader;
import org.briarproject.briar.api.blog.BlogPostHeader;

import javax.annotation.Nullable;

import static android.support.v4.app.ActivityOptionsCompat.makeSceneTransitionAnimation;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static org.briarproject.briar.android.activity.BriarActivity.GROUP_ID;
import static org.briarproject.briar.android.blog.BasePostFragment.POST_ID;
import static org.briarproject.briar.android.util.UiUtils.TEASER_LENGTH;
import static org.briarproject.briar.android.util.UiUtils.getSpanned;
import static org.briarproject.briar.android.util.UiUtils.getTeaser;
import static org.briarproject.briar.android.util.UiUtils.makeLinksClickable;
import static org.briarproject.briar.api.blog.MessageType.POST;

@UiThread
class BlogPostViewHolder extends RecyclerView.ViewHolder {

	private final Context ctx;
	private final ViewGroup layout;
	private final AuthorView reblogger;
	private final AuthorView author;
	private final ImageView reblogButton;
	private final TextView body;
	private final ViewGroup commentContainer;

	private OnBlogPostClickListener listener;

	BlogPostViewHolder(View v) {
		super(v);

		ctx = v.getContext();
		layout = (ViewGroup) v.findViewById(R.id.postLayout);
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

	void updateDate(long time) {
		author.setDate(time);
	}

	void setTransitionName(MessageId id) {
		ViewCompat.setTransitionName(layout, getTransitionName(id));
	}

	private String getTransitionName(MessageId id) {
		return "blogPost" + id.hashCode();
	}

	void bindItem(@Nullable final BlogPostItem item) {
		if (item == null) return;

		setTransitionName(item.getId());
		if (listener != null) {
			layout.setClickable(true);
			layout.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					listener.onBlogPostClick(item);
				}
			});
		}

		// author and date
		BlogPostHeader post = item.getPostHeader();
		Author a = post.getAuthor();
		author.setAuthor(a);
		author.setAuthorStatus(post.getAuthorStatus());
		author.setDate(post.getTimestamp());
		author.setPersona(
				item.isRssFeed() ? AuthorView.RSS_FEED : AuthorView.NORMAL);
		// TODO make author clickable more often #624
		if (item.getHeader().getType() == POST) {
			author.setBlogLink(post.getGroupId());
		} else {
			author.unsetBlogLink();
		}

		// post body
		Spanned bodyText = getSpanned(item.getBody());
		if (listener == null) {
			body.setText(bodyText);
			body.setTextIsSelectable(true);
			makeLinksClickable(body);
		} else {
			body.setTextIsSelectable(false);
			if (bodyText.length() > TEASER_LENGTH)
				bodyText = getTeaser(ctx, bodyText);
			body.setText(bodyText);
		}

		// reblog button
		reblogButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent i = new Intent(ctx, ReblogActivity.class);
				i.putExtra(GROUP_ID, item.getGroupId().getBytes());
				i.putExtra(POST_ID, item.getId().getBytes());

				if (Build.VERSION.SDK_INT >= 23) {
					ActivityOptionsCompat options =
							makeSceneTransitionAnimation((Activity) ctx, layout,
									getTransitionName(item.getId()));
					ActivityCompat.startActivity((Activity) ctx, i,
							options.toBundle());
				} else {
					// work-around for android bug #224270
					ctx.startActivity(i);
				}
			}
		});

		// comments
		commentContainer.removeAllViews();
		if (item instanceof BlogCommentItem) {
			onBindComment((BlogCommentItem) item);
		} else {
			reblogger.setVisibility(GONE);
		}
	}

	private void onBindComment(final BlogCommentItem item) {
		// reblogger
		reblogger.setAuthor(item.getAuthor());
		reblogger.setAuthorStatus(item.getAuthorStatus());
		reblogger.setDate(item.getTimestamp());
		reblogger.setBlogLink(item.getGroupId());
		reblogger.setVisibility(VISIBLE);
		reblogger.setPersona(AuthorView.REBLOGGER);

		author.setPersona(item.getHeader().getRootPost().isRssFeed() ?
				AuthorView.RSS_FEED_REBLOGGED :
				AuthorView.COMMENTER);

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
			if (listener == null) body.setTextIsSelectable(true);

			commentContainer.addView(v);
		}
	}
}
