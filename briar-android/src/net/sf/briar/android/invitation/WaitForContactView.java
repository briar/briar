package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import net.sf.briar.R;
import android.content.Context;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class WaitForContactView extends AddContactView {

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
		icon.setPadding(10, 10, 10, 10);
		icon.setImageResource(R.drawable.navigation_accept);
		innerLayout.addView(icon);

		TextView failed = new TextView(ctx);
		failed.setTextSize(20);
		failed.setText(R.string.connected_to_contact);
		innerLayout.addView(failed);
		addView(innerLayout);

		TextView yourCode = new TextView(ctx);
		yourCode.setGravity(CENTER_HORIZONTAL);
		yourCode.setText(R.string.your_confirmation_code);
		addView(yourCode);

		TextView code = new TextView(ctx);
		code.setGravity(CENTER_HORIZONTAL);
		code.setTextSize(50);
		int localCode = container.getLocalConfirmationCode();
		code.setText(String.format("%06d", localCode));
		addView(code);

		innerLayout = new LinearLayout(ctx);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);

		ProgressBar progress = new ProgressBar(ctx);
		progress.setIndeterminate(true);
		progress.setPadding(0, 10, 10, 0);
		innerLayout.addView(progress);

		TextView connecting = new TextView(ctx);
		connecting.setText(R.string.waiting_for_contact);
		innerLayout.addView(connecting);
		addView(innerLayout);
	}
}
