package org.briarproject.android.view;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.blogs.BlogActivity;
import org.briarproject.android.util.AndroidUtils;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.Author.Status;
import org.briarproject.api.sync.GroupId;

import de.hdodenhof.circleimageview.CircleImageView;
import im.delight.android.identicons.IdenticonDrawable;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.graphics.Typeface.BOLD;
import static android.graphics.Typeface.NORMAL;
import static android.support.v4.app.ActivityOptionsCompat.makeCustomAnimation;
import static android.util.TypedValue.COMPLEX_UNIT_PX;
import static org.briarproject.android.BriarActivity.GROUP_ID;
import static org.briarproject.api.identity.Author.Status.OURSELVES;

@UiThread
public class AuthorView extends RelativeLayout {

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
		trustIndicator.setTrustLevel(status);
		if (status == OURSELVES) {
			authorName.setTypeface(authorNameTypeface, BOLD);
		} else {
			authorName.setTypeface(authorNameTypeface, NORMAL);
		}

		invalidate();
		requestLayout();
	}

	public void setDate(long date) {
		this.date.setText(AndroidUtils.formatDate(getContext(), date));

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
				ActivityOptionsCompat options =
						makeCustomAnimation(getContext(),
								android.R.anim.slide_in_left,
								android.R.anim.slide_out_right);
				Intent[] intents = {i};
				ContextCompat.startActivities(getContext(), intents,
								options.toBundle());
			}
		});
	}

	public void unsetBlogLink() {
		setClickable(false);
		setBackgroundResource(android.R.color.transparent);
		setOnClickListener(null);
	}

	private void setPersona(int persona) {
		switch (persona) {
			// reblogger
			case 1:
				avatarIcon.setVisibility(VISIBLE);
				break;
			// commenter
			case 2:
				LayoutParams params = (LayoutParams) avatar.getLayoutParams();
				int size = getResources().getDimensionPixelSize(
						R.dimen.blogs_avatar_comment_size);
				params.height = size;
				params.width = size;
				avatar.setLayoutParams(params);
				float textSize = getResources()
						.getDimensionPixelSize(R.dimen.text_size_tiny);
				authorName.setTextSize(COMPLEX_UNIT_PX, textSize);
				break;
			// list
			case 3:
				date.setVisibility(GONE);
				params = (LayoutParams) avatar.getLayoutParams();
				size = getResources().getDimensionPixelSize(
						R.dimen.listitem_picture_size_small);
				params.height = size;
				params.width = size;
				avatar.setLayoutParams(params);
				textSize = getResources()
						.getDimensionPixelSize(R.dimen.text_size_medium);
				authorName.setTextSize(COMPLEX_UNIT_PX, textSize);
				params = (LayoutParams) authorName.getLayoutParams();
				params.addRule(CENTER_VERTICAL);
				authorName.setLayoutParams(params);
				params = (LayoutParams) trustIndicator.getLayoutParams();
				params.addRule(CENTER_VERTICAL);
				trustIndicator.setLayoutParams(params);
				break;
		}
	}

}
