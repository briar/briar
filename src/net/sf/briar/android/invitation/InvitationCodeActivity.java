package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.widget.LinearLayout.VERTICAL;
import net.sf.briar.R;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.TextView;

public class InvitationCodeActivity extends Activity
implements CodeEntryListener {

	private final InvitationManager manager =
			InvitationManagerFactory.getInvitationManager();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		TextView yourCode = new TextView(this);
		yourCode.setGravity(CENTER_HORIZONTAL);
		yourCode.setText(R.string.your_invitation_code);
		layout.addView(yourCode);

		TextView code = new TextView(this);
		code.setGravity(CENTER_HORIZONTAL);
		code.setTextSize(50);
		code.setText(manager.getLocalInvitationCode());
		layout.addView(code);

		CodeEntryWidget codeEntry = new CodeEntryWidget(this);
		Resources res = getResources();
		codeEntry.init(this, res.getString(R.string.enter_invitation_code));
		layout.addView(codeEntry);

		setContentView(layout);
	}

	public void codeEntered(String code) {
		manager.setRemoteInvitationCode(code);
		Intent intent = new Intent(this, ConnectionActivity.class);
		intent.putExtras(getIntent().getExtras());
		startActivity(intent);
		finish();
	}
}
