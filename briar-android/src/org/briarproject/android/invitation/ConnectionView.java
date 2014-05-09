package org.briarproject.android.invitation;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;

import org.briarproject.R;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

class ConnectionView extends AddContactView {

	ConnectionView(Context ctx) {
		super(ctx);
	}

	void populate() {
		removeAllViews();
		Context ctx = getContext();
		TextView yourCode = new TextView(ctx);
		yourCode.setGravity(CENTER_HORIZONTAL);
		yourCode.setPadding(pad, pad, pad, pad);
		yourCode.setText(R.string.your_invitation_code);
		addView(yourCode);

		TextView code = new TextView(ctx);
		code.setGravity(CENTER_HORIZONTAL);
		code.setTextSize(50);
		code.setPadding(pad, 0, pad, pad);
		int localCode = container.getLocalInvitationCode();
		code.setText(String.format("%06d", localCode));
		addView(code);

		LinearLayout innerLayout = new LinearLayout(ctx);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);

		ProgressBar progress = new ProgressBar(ctx);
		progress.setPadding(pad, pad, pad, pad);
		progress.setIndeterminate(true);
		innerLayout.addView(progress);

		TextView connecting = new TextView(ctx);
		int remoteCode = container.getRemoteInvitationCode();
		String format = container.getString(R.string.searching_format);
		connecting.setText(String.format(format, remoteCode));
		innerLayout.addView(connecting);

		addView(innerLayout);
	}
}
