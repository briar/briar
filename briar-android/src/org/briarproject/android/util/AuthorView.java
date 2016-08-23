package org.briarproject.android.util;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.Author.Status;

import de.hdodenhof.circleimageview.CircleImageView;
import im.delight.android.identicons.IdenticonDrawable;

import static org.briarproject.api.identity.Author.Status.OURSELVES;

public class AuthorView extends RelativeLayout {

	private final CircleImageView avatar;
	private final TextView authorName;
	private final TextView date;
	private final TrustIndicatorView trustIndicator;

	public AuthorView(Context context, AttributeSet attrs) {
		super(context, attrs);

		LayoutInflater inflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater
				.inflate(R.layout.author_view, this, true);

		avatar = (CircleImageView) findViewById(R.id.avatar);
		authorName = (TextView) findViewById(R.id.authorName);
		date = (TextView) findViewById(R.id.dateView);
		trustIndicator = (TrustIndicatorView) findViewById(R.id.trustIndicator);
	}

	public AuthorView(Context context) {
		this(context, null);
	}

	public void setAuthor(Author author) {
		authorName.setText(author.getName());
		IdenticonDrawable d = new IdenticonDrawable(author.getId().getBytes());
		avatar.setImageDrawable(d);
	}

	public void setAuthorStatus(Status status) {
		trustIndicator.setTrustLevel(status);
		if (status == OURSELVES) {
			authorName.setTypeface(authorName.getTypeface(), Typeface.BOLD);
		}

		invalidate();
		requestLayout();
	}

	public void setDate(long date) {
		this.date.setText(AndroidUtils.formatDate(getContext(), date));
	}

}
