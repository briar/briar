package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.widget.LinearLayout.HORIZONTAL;
import net.sf.briar.R;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class ConnectionActivity extends Activity implements ConnectionListener {

	private final InvitationManager manager =
			InvitationManagerFactory.getInvitationManager();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_connection);
		LinearLayout outerLayout = (LinearLayout) findViewById(
				R.id.connection_container);

		Bundle b = getIntent().getExtras();
		String networkName = b.getString(
				"net.sf.briar.android.invitation.NETWORK_NAME");
		boolean useBluetooth = b.getBoolean(
				"net.sf.briar.android.invitation.USE_BLUETOOTH");

		TextView yourCode = new TextView(this);
		yourCode.setGravity(CENTER_HORIZONTAL);
		yourCode.setText(R.string.your_invitation_code);
		outerLayout.addView(yourCode);
		TextView code = new TextView(this);
		code.setGravity(CENTER_HORIZONTAL);
		code.setText(manager.getLocalInvitationCode());
		code.setTextSize(50);
		outerLayout.addView(code);

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
			String text = res.getString(R.string.connecting_wifi);
			text = String.format(text, networkName);
			connecting.setText(text);
			innerLayout.addView(connecting);
			outerLayout.addView(innerLayout);
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
			outerLayout.addView(innerLayout);
			manager.startBluetoothConnectionWorker(this);
		}

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
