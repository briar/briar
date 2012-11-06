package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import net.sf.briar.R;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class WaitForContactActivity extends Activity
implements ConfirmationListener {

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

		TextView failed = new TextView(this);
		failed.setTextSize(20);
		failed.setText(R.string.connected_to_contact);
		innerLayout.addView(failed);
		layout.addView(innerLayout);

		TextView yourCode = new TextView(this);
		yourCode.setGravity(CENTER_HORIZONTAL);
		yourCode.setText(R.string.your_confirmation_code);
		layout.addView(yourCode);

		TextView code = new TextView(this);
		code.setGravity(CENTER_HORIZONTAL);
		code.setTextSize(50);
		InvitationManager im = InvitationManagerFactory.getInvitationManager();
		code.setText(im.getLocalConfirmationCode());
		layout.addView(code);

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
		layout.addView(innerLayout);

		setContentView(layout);

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
