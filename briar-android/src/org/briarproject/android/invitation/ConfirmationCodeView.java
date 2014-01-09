package org.briarproject.android.invitation;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;

import org.briarproject.R;

import android.content.Context;
import android.content.res.Resources;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

class ConfirmationCodeView extends AddContactView implements CodeEntryListener {

	ConfirmationCodeView(Context ctx) {
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
		yourCode.setPadding(pad, pad, pad, pad);
		yourCode.setText(R.string.your_confirmation_code);
		addView(yourCode);

		TextView code = new TextView(ctx);
		code.setGravity(CENTER_HORIZONTAL);
		code.setTextSize(50);
		code.setPadding(pad, 0, pad, pad);
		int localCode = container.getLocalConfirmationCode();
		code.setText(String.format("%06d", localCode));
		addView(code);

		CodeEntryView codeEntry = new CodeEntryView(ctx);
		Resources res = getResources();
		codeEntry.init(this, res.getString(R.string.enter_confirmation_code));
		addView(codeEntry);
	}

	public void codeEntered(int remoteCode) {
		container.remoteConfirmationCodeEntered(remoteCode);
	}
}
