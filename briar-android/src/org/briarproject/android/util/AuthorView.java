package org.briarproject.android.util;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;

import javax.inject.Inject;

import im.delight.android.identicons.IdenticonDrawable;
import roboguice.RoboGuice;

public class AuthorView extends FrameLayout {

	@Inject private CryptoComponent crypto;
	private ImageView avatarView;
	private TextView nameView;
	private ImageView statusView;

	public AuthorView(Context ctx) {
		super(ctx);

		initViews();
	}

	public AuthorView(Context context, AttributeSet attrs) {
		super(context, attrs);

		initViews();
	}

	public AuthorView(Context context, AttributeSet attrs,
							 int defStyle) {
		super(context, attrs, defStyle);

		initViews();
	}

	private void initViews() {
		RoboGuice.injectMembers(getContext(), this);
		if (isInEditMode())
			return;

		View v = LayoutInflater.from(getContext()).inflate(
				R.layout.author_view, this, true);

		avatarView = (ImageView) v.findViewById(R.id.avatarView);
		nameView = (TextView) v.findViewById(R.id.nameView);
		statusView = (ImageView) v.findViewById(R.id.statusView);
	}

	public void init(String name, AuthorId id, Author.Status status) {
		if (name == null) {
			nameView.setText(R.string.anonymous);
		} else {
			nameView.setText(name);
			avatarView.setImageDrawable(
					new IdenticonDrawable(crypto, id.getBytes()));
		}

		switch(status) {
		case ANONYMOUS:
			statusView.setImageResource(R.drawable.identity_anonymous);
			break;
		case UNKNOWN:
			statusView.setImageResource(R.drawable.identity_unknown);
			break;
		case UNVERIFIED:
			statusView.setImageResource(R.drawable.identity_unverified);
			break;
		case VERIFIED:
			statusView.setImageResource(R.drawable.identity_verified);
			break;
		}
	}
}
