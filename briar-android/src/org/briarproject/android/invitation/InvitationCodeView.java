package org.briarproject.android.invitation;

import static android.view.Gravity.CENTER_HORIZONTAL;
import org.briarproject.R;
import android.content.Context;
import android.content.res.Resources;
import android.widget.TextView;

class InvitationCodeView extends AddContactView implements CodeEntryListener {

	InvitationCodeView(Context ctx) {
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

		CodeEntryView codeEntry = new CodeEntryView(ctx);
		Resources res = getResources();
		codeEntry.init(this, res.getString(R.string.enter_invitation_code));
		addView(codeEntry);
	}

	public void codeEntered(int remoteCode) {
		container.remoteInvitationCodeEntered(remoteCode);
	}
}
