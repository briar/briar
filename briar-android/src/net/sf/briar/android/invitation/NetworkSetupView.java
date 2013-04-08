package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER;
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_WRAP;
import static net.sf.briar.android.widgets.CommonLayoutParams.WRAP_WRAP;
import net.sf.briar.R;
import net.sf.briar.android.LocalAuthorSpinnerAdapter;
import net.sf.briar.android.identity.CreateIdentityActivity;
import net.sf.briar.api.LocalAuthor;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

public class NetworkSetupView extends AddContactView
implements WifiStateListener, BluetoothStateListener, OnItemSelectedListener,
OnClickListener {

	private LocalAuthorSpinnerAdapter adapter = null;
	private Spinner spinner = null;
	private Button continueButton = null;

	NetworkSetupView(Context ctx) {
		super(ctx);
	}

	void populate() {
		removeAllViews();
		Context ctx = getContext();

		LinearLayout innerLayout = new LinearLayout(ctx);
		innerLayout.setLayoutParams(MATCH_WRAP);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);

		TextView yourIdentity = new TextView(ctx);
		yourIdentity.setTextSize(18);
		yourIdentity.setPadding(10, 10, 10, 10);
		yourIdentity.setText(R.string.your_identity);
		innerLayout.addView(yourIdentity);

		adapter = new LocalAuthorSpinnerAdapter(ctx);
		spinner = new Spinner(ctx);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(this);
		container.loadLocalAuthorList(adapter);
		innerLayout.addView(spinner);
		addView(innerLayout);

		WifiWidget wifi = new WifiWidget(ctx);
		wifi.init(this);
		addView(wifi);

		BluetoothWidget bluetooth = new BluetoothWidget(ctx);
		bluetooth.init(this);
		addView(bluetooth);

		continueButton = new Button(ctx);
		continueButton.setLayoutParams(WRAP_WRAP);
		continueButton.setText(R.string.continue_button);
		continueButton.setOnClickListener(this);
		enableOrDisableContinueButton();
		addView(continueButton);
	}

	public void wifiStateChanged(final String networkName) {
		container.runOnUiThread(new Runnable() {
			public void run() {
				container.setNetworkName(networkName);
				enableOrDisableContinueButton();
			}
		});
	}

	public void bluetoothStateChanged(final boolean enabled) {
		container.runOnUiThread(new Runnable() {
			public void run() {
				container.setUseBluetooth(enabled);
				enableOrDisableContinueButton();
			}
		});
	}

	private void enableOrDisableContinueButton() {
		if(continueButton == null) return; // Activity not created yet
		boolean useBluetooth = container.getUseBluetooth();
		String networkName = container.getNetworkName();
		if(useBluetooth || networkName != null) continueButton.setEnabled(true);
		else continueButton.setEnabled(false);
	}

	public void onItemSelected(AdapterView<?> parent, View view, int position,
			long id) {
		LocalAuthor item = adapter.getItem(position);
		if(item == null) {
			Intent i = new Intent(container, CreateIdentityActivity.class);
			container.startActivity(i);
		} else {
			container.setLocalAuthorId(item.getId());
		}
	}

	public void onNothingSelected(AdapterView<?> parent) {
		container.setLocalAuthorId(null);
	}

	public void onClick(View view) {
		container.setView(new InvitationCodeView(container));
	}
}
