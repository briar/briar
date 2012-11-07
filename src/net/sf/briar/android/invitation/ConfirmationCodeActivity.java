package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import net.sf.briar.R;
import roboguice.activity.RoboActivity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.inject.Inject;

public class ConfirmationCodeActivity extends RoboActivity
implements CodeEntryListener {

	@Inject private InvitationManager manager;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		LinearLayout innerLayout = new LinearLayout(this);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);

		ImageView icon = new ImageView(this);
		icon.setPadding(10, 10, 10, 10);
		icon.setImageResource(R.drawable.navigation_accept);
		innerLayout.addView(icon);

		TextView connected = new TextView(this);
		connected.setTextSize(20);
		connected.setText(R.string.connected_to_contact);
		innerLayout.addView(connected);
		layout.addView(innerLayout);

		TextView yourCode = new TextView(this);
		yourCode.setGravity(CENTER_HORIZONTAL);
		yourCode.setText(R.string.your_confirmation_code);
		layout.addView(yourCode);

		TextView code = new TextView(this);
		code.setGravity(CENTER_HORIZONTAL);
		code.setTextSize(50);
		code.setText(manager.getLocalConfirmationCode());
		layout.addView(code);

		CodeEntryWidget codeEntry = new CodeEntryWidget(this);
		Resources res = getResources();
		codeEntry.init(this, res.getString(R.string.enter_confirmation_code));
		layout.addView(codeEntry);

		setContentView(layout);
	}

	public void codeEntered(String code) {
		if(code.equals(manager.getRemoteConfirmationCode())) {
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
