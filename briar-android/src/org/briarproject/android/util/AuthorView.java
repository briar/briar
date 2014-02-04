package org.briarproject.android.util;

import org.briarproject.R;
import org.briarproject.api.Author;

import android.content.Context;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AuthorView extends LinearLayout {

	public AuthorView(Context ctx) {
		super(ctx);
	}

	public void init(String name, Author.Status status) {
		Context ctx = getContext();
		int pad = LayoutUtils.getPadding(ctx);
		setOrientation(VERTICAL);
		TextView nameView = new TextView(ctx);
		// Give me all the unused width
		nameView.setTextSize(18);
		nameView.setMaxLines(1);
		nameView.setPadding(pad, pad, pad, pad);
		if(name == null) nameView.setText(R.string.anonymous);
		else nameView.setText(name);
		addView(nameView);
		LinearLayout statusLayout = new LinearLayout(ctx);
		statusLayout.setOrientation(HORIZONTAL);
		ImageView statusView = new ImageView(ctx);
		statusView.setPadding(pad, 0, pad, pad);
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
		statusLayout.addView(statusView);
		statusLayout.addView(new ElasticHorizontalSpace(ctx));
		addView(statusLayout);
	}
}
