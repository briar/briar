package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.widget.LinearLayout.HORIZONTAL;
import net.sf.briar.R;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ConfirmationCodeActivity extends Activity
implements CodeEntryListener {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_connection_succeeded);
		LinearLayout outerLayout = (LinearLayout) findViewById(
				R.id.connection_succeeded_container);

		LinearLayout innerLayout = new LinearLayout(this);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);
		ImageView icon = new ImageView(this);
		icon.setImageResource(R.drawable.iconic_check_alt_green);
		icon.setPadding(10, 10, 10, 10);
		innerLayout.addView(icon);
		TextView failed = new TextView(this);
		failed.setTextSize(20);
		failed.setText(R.string.connected_to_contact);
		innerLayout.addView(failed);
		outerLayout.addView(innerLayout);

		TextView checkNetwork = new TextView(this);
		checkNetwork.setGravity(CENTER_HORIZONTAL);
		checkNetwork.setText(R.string.your_confirmation_code);
		outerLayout.addView(checkNetwork);
		TextView code = new TextView(this);
		code.setGravity(CENTER_HORIZONTAL);
		InvitationManager im = InvitationManagerFactory.getInvitationManager();
		String localConfirmationCode = im.getLocalConfirmationCode();
		code.setText(localConfirmationCode);
		code.setTextSize(50);
		outerLayout.addView(code);
		CodeEntryWidget codeEntry = new CodeEntryWidget(this);
		Resources res = getResources();
		codeEntry.init(this, res.getString(R.string.enter_confirmation_code));
		outerLayout.addView(codeEntry);
	}

	public void codeEntered(String code) {
		InvitationManager im = InvitationManagerFactory.getInvitationManager();
		String remoteConfirmationCode = im.getRemoteConfirmationCode();
		if(code.equals(String.valueOf(remoteConfirmationCode))) {
			Intent intent = new Intent(this, WaitForContactActivity.class);
			intent.putExtras(getIntent().getExtras());
			startActivity(intent);
		} else {
			Intent intent = new Intent(this, CodesDoNotMatchActivity.class);
			intent.putExtras(getIntent().getExtras());
			startActivity(intent);
		}
		finish();
	}
}
