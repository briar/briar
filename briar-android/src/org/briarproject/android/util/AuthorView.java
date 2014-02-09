package org.briarproject.android.util;

import static android.text.TextUtils.TruncateAt.END;

import org.briarproject.R;
import org.briarproject.api.Author;

import android.content.Context;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class AuthorView extends RelativeLayout {

	public AuthorView(Context ctx) {
		super(ctx);
	}

	public void init(String name, Author.Status status) {
		Context ctx = getContext();
		int pad = LayoutUtils.getPadding(ctx);

		TextView nameView = new TextView(ctx);
		nameView.setId(1);
		nameView.setTextSize(18);
		nameView.setSingleLine();
		nameView.setEllipsize(END);
		nameView.setPadding(pad, pad, pad, pad);
		if(name == null) nameView.setText(R.string.anonymous);
		else nameView.setText(name);
		LayoutParams leftOf = CommonLayoutParams.relative();
		leftOf.addRule(ALIGN_PARENT_LEFT);
		leftOf.addRule(CENTER_VERTICAL);
		leftOf.addRule(LEFT_OF, 2);
		addView(nameView, leftOf);

		ImageView statusView = new ImageView(ctx);
		statusView.setId(2);
		statusView.setPadding(0, pad, pad, pad);
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
		LayoutParams right = CommonLayoutParams.relative();
		right.addRule(ALIGN_PARENT_RIGHT);
		right.addRule(CENTER_VERTICAL);
		addView(statusView, right);
	}
}
