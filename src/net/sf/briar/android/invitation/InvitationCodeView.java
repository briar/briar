package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER_HORIZONTAL;
import net.sf.briar.R;
import android.content.Context;
import android.content.res.Resources;
import android.widget.TextView;

public class InvitationCodeView extends AddContactView
implements CodeEntryListener {

	private int localCode = -1;

	InvitationCodeView(Context ctx) {
		super(ctx);
	}

	void init(AddContactActivity container) {
		localCode = container.generateLocalInvitationCode();
		super.init(container);
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
		code.setText(String.format("%06d", localCode));
		addView(code);

		CodeEntryWidget codeEntry = new CodeEntryWidget(ctx);
		Resources res = getResources();
		codeEntry.init(this, res.getString(R.string.enter_invitation_code));
		addView(codeEntry);
	}

	public void codeEntered(int remoteCode) {
		container.remoteInvitationCodeEntered(remoteCode);
	}
}
