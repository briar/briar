package org.briarproject.android.util;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.widget.ImageView;

import org.briarproject.R;
import org.briarproject.api.identity.Author.Status;

public class TrustIndicatorView extends ImageView {

	public TrustIndicatorView(Context context) {
		super(context);
	}

	public TrustIndicatorView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public TrustIndicatorView(Context context, AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	public void setTrustLevel(Status status) {
		int res;
		switch (status) {
			case ANONYMOUS:
				res = R.drawable.trust_indicator_anonymous;
				break;
			case UNVERIFIED:
				res = R.drawable.trust_indicator_unverified;
				break;
			case VERIFIED:
				res = R.drawable.trust_indicator_verified;
				break;
			default:
				res = R.drawable.trust_indicator_unknown;
		}
		setImageDrawable(ContextCompat.getDrawable(getContext(), res));
	}

}
