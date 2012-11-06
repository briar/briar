package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.widget.LinearLayout.HORIZONTAL;
import net.sf.briar.R;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class WaitForContactActivity extends Activity
implements ConfirmationListener {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_wait_for_contact);
		LinearLayout outerLayout = (LinearLayout) findViewById(
				R.id.wait_for_contact_container);

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

		TextView yourCode = new TextView(this);
		yourCode.setGravity(CENTER_HORIZONTAL);
		yourCode.setText(R.string.your_confirmation_code);
		outerLayout.addView(yourCode);
		TextView code = new TextView(this);
		code.setGravity(CENTER_HORIZONTAL);
		InvitationManager im = InvitationManagerFactory.getInvitationManager();
		String localConfirmationCode = im.getLocalConfirmationCode();
		code.setText(localConfirmationCode);
		code.setTextSize(50);
		outerLayout.addView(code);

		innerLayout = new LinearLayout(this);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);
		ProgressBar progress = new ProgressBar(this);
		progress.setIndeterminate(true);
		progress.setPadding(0, 10, 10, 0);
		innerLayout.addView(progress);
		TextView connecting = new TextView(this);
		connecting.setText(R.string.waiting_for_contact);
		innerLayout.addView(connecting);
		outerLayout.addView(innerLayout);

		im.startConfirmationWorker(this);
	}

	public void confirmationReceived() {
		startActivity(new Intent(this, ContactAddedActivity.class));
		finish();
	}

	public void confirmationNotReceived() {
		Intent intent = new Intent(this, CodesDoNotMatchActivity.class);
		intent.putExtras(getIntent().getExtras());
		startActivity(intent);
		finish();
	}
}
