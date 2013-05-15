package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import net.sf.briar.R;
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
		yourCode.setTextSize(14);
		yourCode.setPadding(10, 10, 10, 10);
		yourCode.setText(R.string.your_invitation_code);
		addView(yourCode);

		TextView code = new TextView(ctx);
		code.setGravity(CENTER_HORIZONTAL);
		code.setTextSize(50);
		code.setPadding(10, 0, 10, 10);
		int localCode = container.getLocalInvitationCode();
		code.setText(String.format("%06d", localCode));
		addView(code);

		String networkName = container.getNetworkName();
		if(networkName != null) {
			LinearLayout innerLayout = new LinearLayout(ctx);
			innerLayout.setOrientation(HORIZONTAL);
			innerLayout.setGravity(CENTER);

			ProgressBar progress = new ProgressBar(ctx);
			progress.setIndeterminate(true);
			progress.setPadding(10, 10, 10, 10);
			innerLayout.addView(progress);

			TextView connecting = new TextView(ctx);
			String format = getResources().getString(
					R.string.format_connecting_wifi);
			connecting.setText(String.format(format, networkName));
			innerLayout.addView(connecting);

			addView(innerLayout);
		}

		if(container.isBluetoothEnabled()) {
			LinearLayout innerLayout = new LinearLayout(ctx);
			innerLayout.setOrientation(HORIZONTAL);
			innerLayout.setGravity(CENTER);

			ProgressBar progress = new ProgressBar(ctx);
			progress.setPadding(10, 10, 10, 10);
			progress.setIndeterminate(true);
			innerLayout.addView(progress);

			TextView connecting = new TextView(ctx);
			connecting.setText(R.string.connecting_bluetooth);
			innerLayout.addView(connecting);

			addView(innerLayout);
		}
	}
}
