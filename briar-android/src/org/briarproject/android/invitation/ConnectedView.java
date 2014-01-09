package org.briarproject.android.invitation;

import static android.view.Gravity.CENTER;
import org.briarproject.R;
import android.content.Context;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

class ConnectedView extends AddContactView {

	ConnectedView(Context ctx) {
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

		innerLayout = new LinearLayout(ctx);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);

		ProgressBar progress = new ProgressBar(ctx);
		progress.setIndeterminate(true);
		progress.setPadding(pad, pad, pad, pad);
		innerLayout.addView(progress);

		TextView connecting = new TextView(ctx);
		connecting.setText(R.string.calculating_confirmation_code);
		innerLayout.addView(connecting);
		addView(innerLayout);
	}
}
