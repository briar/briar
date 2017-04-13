package org.briarproject.briar.android.view;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.support.annotation.DimenRes;
import android.support.annotation.UiThread;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.Author.Status;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.blog.BlogActivity;
import org.briarproject.briar.android.util.UiUtils;

import javax.annotation.Nullable;

import de.hdodenhof.circleimageview.CircleImageView;
import im.delight.android.identicons.IdenticonDrawable;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.graphics.Typeface.BOLD;
import static android.util.TypedValue.COMPLEX_UNIT_PX;
import static org.briarproject.bramble.api.identity.Author.Status.NONE;
import static org.briarproject.bramble.api.identity.Author.Status.OURSELVES;
import static org.briarproject.briar.android.activity.BriarActivity.GROUP_ID;

@UiThread
public class AuthorView extends RelativeLayout {

	public static final int NORMAL = 0;
	public static final int REBLOGGER = 1;
	public static final int COMMENTER = 2;
	public static final int LIST = 3;
	public static final int RSS_FEED = 4;
	public static final int RSS_FEED_REBLOGGED = 5;

	private final CircleImageView avatar;
	private final ImageView avatarIcon;
	private final TextView authorName;
	private final Typeface authorNameTypeface;
	private final TextView date;
	private final TrustIndicatorView trustIndicator;

	public AuthorView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);

		LayoutInflater inflater = (LayoutInflater) context
				.getSystemService(LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.author_view, this, true);

		avatar = (CircleImageView) findViewById(R.id.avatar);
		avatarIcon = (ImageView) findViewById(R.id.avatarIcon);
		authorName = (TextView) findViewById(R.id.authorName);
		authorNameTypeface = authorName.getTypeface();
		date = (TextView) findViewById(R.id.dateView);
		trustIndicator = (TrustIndicatorView) findViewById(R.id.trustIndicator);

		TypedArray attributes =
				context.obtainStyledAttributes(attrs, R.styleable.AuthorView);
		int persona = attributes.getInteger(R.styleable.AuthorView_persona, 0);
		setPersona(persona);
		attributes.recycle();
	}

	public AuthorView(Context context) {
		this(context, null);
	}

	public void setAuthor(Author author) {
		authorName.setText(author.getName());
		IdenticonDrawable d = new IdenticonDrawable(author.getId().getBytes());
		avatar.setImageDrawable(d);

		invalidate();
		requestLayout();
	}

	public void setAuthorStatus(Status status) {
		if (status != NONE) {
			trustIndicator.setTrustLevel(status);
			trustIndicator.setVisibility(VISIBLE);
		} else {
			trustIndicator.setVisibility(GONE);
		}

		if (status == OURSELVES) {
			authorName.setTypeface(authorNameTypeface, BOLD);
		} else {
			authorName.setTypeface(authorNameTypeface, NORMAL);
		}

		invalidate();
		requestLayout();
	}

	public void setDate(long date) {
		this.date.setText(UiUtils.formatDate(getContext(), date));

		invalidate();
		requestLayout();
	}

	public void setBlogLink(final GroupId groupId) {
		setClickable(true);
		TypedValue outValue = new TypedValue();
		getContext().getTheme().resolveAttribute(
				android.R.attr.selectableItemBackground, outValue, true);
		setBackgroundResource(outValue.resourceId);
		setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent i = new Intent(getContext(), BlogActivity.class);
				i.putExtra(GROUP_ID, groupId.getBytes());
				i.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
				getContext().startActivity(i);
			}
		});
	}

	public void unsetBlogLink() {
		setClickable(false);
		setBackgroundResource(android.R.color.transparent);
		setOnClickListener(null);
	}

	/**
	 * Styles this view for a different persona.
	 *
	 * Attention: RSS_FEED and RSS_FEED_REBLOGGED change the avatar
	 *            and override the one set by
	 *            {@link AuthorView#setAuthor(Author)}.
	 */
	public void setPersona(int persona) {
		switch (persona) {
			case NORMAL:
				avatarIcon.setVisibility(INVISIBLE);
				date.setVisibility(VISIBLE);
				setAvatarSize(R.dimen.blogs_avatar_normal_size);
				setTextSize(authorName, R.dimen.text_size_small);
				setCenterVertical(authorName, false);
				setCenterVertical(trustIndicator, false);
				break;
			case REBLOGGER:
				avatarIcon.setVisibility(VISIBLE);
				date.setVisibility(VISIBLE);
				setAvatarSize(R.dimen.blogs_avatar_normal_size);
				setTextSize(authorName, R.dimen.text_size_small);
				setCenterVertical(authorName, false);
				setCenterVertical(trustIndicator, false);
				break;
			case COMMENTER:
				avatarIcon.setVisibility(INVISIBLE);
				date.setVisibility(VISIBLE);
				setAvatarSize(R.dimen.blogs_avatar_comment_size);
				setTextSize(authorName, R.dimen.text_size_tiny);
				setCenterVertical(authorName, false);
				setCenterVertical(trustIndicator, false);
				break;
			case LIST:
				avatarIcon.setVisibility(INVISIBLE);
				date.setVisibility(GONE);
				setAvatarSize(R.dimen.listitem_picture_size_small);
				setTextSize(authorName, R.dimen.text_size_medium);
				setCenterVertical(authorName, true);
				setCenterVertical(trustIndicator, true);
				break;
			case RSS_FEED:
				avatarIcon.setVisibility(INVISIBLE);
				date.setVisibility(VISIBLE);
				avatar.setImageResource(R.drawable.ic_rss_feed);
				setAvatarSize(R.dimen.blogs_avatar_normal_size);
				setTextSize(authorName, R.dimen.text_size_small);
				setCenterVertical(authorName, false);
				setCenterVertical(trustIndicator, false);
				break;
			case RSS_FEED_REBLOGGED:
				avatarIcon.setVisibility(INVISIBLE);
				date.setVisibility(VISIBLE);
				avatar.setImageResource(R.drawable.ic_rss_feed);
				setAvatarSize(R.dimen.blogs_avatar_comment_size);
				setTextSize(authorName, R.dimen.text_size_tiny);
				setCenterVertical(authorName, false);
				setCenterVertical(trustIndicator, false);
				break;
		}
	}

	private void setAvatarSize(@DimenRes int res) {
		LayoutParams params = (LayoutParams) avatar.getLayoutParams();
		int size = getResources().getDimensionPixelSize(res);
		params.height = size;
		params.width = size;
		avatar.setLayoutParams(params);
	}

	private void setTextSize(TextView v, @DimenRes int res) {
		float textSize = getResources().getDimensionPixelSize(res);
		v.setTextSize(COMPLEX_UNIT_PX, textSize);
	}

	private void setCenterVertical(View v, boolean center) {
		LayoutParams params = (LayoutParams) v.getLayoutParams();
		params.addRule(CENTER_VERTICAL, center ? RelativeLayout.TRUE : 0);
		v.setLayoutParams(params);
	}

}
