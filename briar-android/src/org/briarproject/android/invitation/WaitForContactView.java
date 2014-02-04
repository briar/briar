package org.briarproject.android.invitation;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;

import org.briarproject.R;

import android.content.Context;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

class WaitForContactView extends AddContactView {

	WaitForContactView(Context ctx) {
		super(ctx);
	}

	void populate() {
		removeAllViews();
		Context ctx = getContext();
		LinearLayout innerLayout = new LinearLayout(ctx);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);

		ImageView icon = new ImageView(ctx);
		icon.setImageResource(R.drawable.navigation_accept);
		innerLayout.addView(icon);

		TextView connected = new TextView(ctx);
		connected.setTextSize(22);
		connected.setPadding(pad, pad, pad, pad);
		connected.setText(R.string.connected_to_contact);
		innerLayout.addView(connected);
		addView(innerLayout);

		TextView yourCode = new TextView(ctx);
		yourCode.setGravity(CENTER_HORIZONTAL);
		yourCode.setTextSize(14);
		yourCode.setPadding(pad, 0, pad, pad);
		yourCode.setText(R.string.your_confirmation_code);
		addView(yourCode);

		TextView code = new TextView(ctx);
		code.setGravity(CENTER_HORIZONTAL);
		code.setTextSize(50);
		code.setPadding(pad, 0, pad, pad);
		int localCode = container.getLocalConfirmationCode();
		code.setText(String.format("%06d", localCode));
		addView(code);

		innerLayout = new LinearLayout(ctx);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);

		ProgressBar progress = new ProgressBar(ctx);
		progress.setIndeterminate(true);
		progress.setPadding(pad, pad, pad, pad);
		innerLayout.addView(progress);

		TextView connecting = new TextView(ctx);
		connecting.setText(R.string.waiting_for_contact);
		innerLayout.addView(connecting);
		addView(innerLayout);
	}
}
