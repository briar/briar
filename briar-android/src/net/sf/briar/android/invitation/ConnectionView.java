package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import net.sf.briar.R;
import android.content.Context;
import android.content.res.Resources;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class ConnectionView extends AddContactView {

	ConnectionView(Context ctx) {
		super(ctx);
	}

	void populate() {
		removeAllViews();
		Context ctx = getContext();
		TextView yourCode = new TextView(ctx);
		yourCode.setGravity(CENTER_HORIZONTAL);
		yourCode.setText(R.string.your_invitation_code);
		addView(yourCode);

		TextView code = new TextView(ctx);
		code.setGravity(CENTER_HORIZONTAL);
		code.setTextSize(50);
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
			progress.setPadding(0, 10, 10, 0);
			innerLayout.addView(progress);

			TextView connecting = new TextView(ctx);
			Resources res = getResources();
			String connectingVia = res.getString(R.string.connecting_wifi);
			connecting.setText(String.format(connectingVia, networkName));
			innerLayout.addView(connecting);

			addView(innerLayout);
		}

		boolean useBluetooth = container.getUseBluetooth();
		if(useBluetooth) {
			LinearLayout innerLayout = new LinearLayout(ctx);
			innerLayout.setOrientation(HORIZONTAL);
			innerLayout.setGravity(CENTER);

			ProgressBar progress = new ProgressBar(ctx);
			progress.setPadding(0, 10, 10, 0);
			progress.setIndeterminate(true);
			innerLayout.addView(progress);

			TextView connecting = new TextView(ctx);
			connecting.setText(R.string.connecting_bluetooth);
			innerLayout.addView(connecting);

			addView(innerLayout);
		}
	}
}
