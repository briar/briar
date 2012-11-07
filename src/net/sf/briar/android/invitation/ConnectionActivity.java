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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.inject.Inject;

public class ConnectionActivity extends RoboActivity
implements ConnectionListener {

	@Inject private InvitationManager manager;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		Bundle b = getIntent().getExtras();
		String networkName = b.getString(
				"net.sf.briar.android.invitation.NETWORK_NAME");
		boolean useBluetooth = b.getBoolean(
				"net.sf.briar.android.invitation.USE_BLUETOOTH");

		TextView yourCode = new TextView(this);
		yourCode.setGravity(CENTER_HORIZONTAL);
		yourCode.setText(R.string.your_invitation_code);
		layout.addView(yourCode);

		TextView code = new TextView(this);
		code.setGravity(CENTER_HORIZONTAL);
		code.setTextSize(50);
		code.setText(manager.getLocalInvitationCode());
		layout.addView(code);

		if(networkName != null) {
			LinearLayout innerLayout = new LinearLayout(this);
			innerLayout.setOrientation(HORIZONTAL);
			innerLayout.setGravity(CENTER);

			ProgressBar progress = new ProgressBar(this);
			progress.setIndeterminate(true);
			progress.setPadding(0, 10, 10, 0);
			innerLayout.addView(progress);

			TextView connecting = new TextView(this);
			Resources res = getResources();
			String connectingVia = res.getString(R.string.connecting_wifi);
			connecting.setText(String.format(connectingVia, networkName));
			innerLayout.addView(connecting);

			layout.addView(innerLayout);
			manager.startWifiConnectionWorker(this);
		}

		if(useBluetooth) {
			LinearLayout innerLayout = new LinearLayout(this);
			innerLayout.setOrientation(HORIZONTAL);
			innerLayout.setGravity(CENTER);

			ProgressBar progress = new ProgressBar(this);
			progress.setPadding(0, 10, 10, 0);
			progress.setIndeterminate(true);
			innerLayout.addView(progress);

			TextView connecting = new TextView(this);
			connecting.setText(R.string.connecting_bluetooth);
			innerLayout.addView(connecting);

			layout.addView(innerLayout);
			manager.startBluetoothConnectionWorker(this);
		}

		setContentView(layout);

		manager.tryToConnect(this);
	}

	public void connectionEstablished() {
		final Intent intent = new Intent(this, ConfirmationCodeActivity.class);
		intent.putExtras(getIntent().getExtras());
		runOnUiThread(new Runnable() {
			public void run() {
				startActivity(intent);
				finish();
			}
		});
	}

	public void connectionNotEstablished() {
		final Intent intent = new Intent(this, ConnectionFailedActivity.class);
		runOnUiThread(new Runnable() {
			public void run() {
				startActivity(intent);
				finish();
			}
		});
	}
}
