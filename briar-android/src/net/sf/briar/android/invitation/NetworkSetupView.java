package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER;
import static net.sf.briar.android.identity.LocalAuthorItem.NEW;
import static net.sf.briar.android.widgets.CommonLayoutParams.MATCH_WRAP;
import static net.sf.briar.android.widgets.CommonLayoutParams.WRAP_WRAP;
import net.sf.briar.R;
import net.sf.briar.android.identity.CreateIdentityActivity;
import net.sf.briar.android.identity.LocalAuthorItem;
import net.sf.briar.android.identity.LocalAuthorSpinnerAdapter;
import net.sf.briar.api.AuthorId;
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

		TextView yourNickname = new TextView(ctx);
		yourNickname.setTextSize(18);
		yourNickname.setPadding(10, 10, 10, 10);
		yourNickname.setText(R.string.your_nickname);
		innerLayout.addView(yourNickname);

		adapter = new LocalAuthorSpinnerAdapter(ctx, false);
		spinner = new Spinner(ctx);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(this);
		container.loadLocalAuthors(adapter);
		innerLayout.addView(spinner);
		addView(innerLayout);

		WifiWidget wifi = new WifiWidget(ctx);
		wifi.init(this);
		addView(wifi);

		BluetoothWidget bluetooth = new BluetoothWidget(ctx);
		bluetooth.init(this);
		addView(bluetooth);

		TextView faceToFace = new TextView(ctx);
		faceToFace.setGravity(CENTER);
		faceToFace.setTextSize(14);
		faceToFace.setPadding(10, 10, 10, 10);
		faceToFace.setText(R.string.fact_to_face);
		addView(faceToFace);

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
		AuthorId localAuthorId = container.getLocalAuthorId();
		boolean useBluetooth = container.getUseBluetooth();
		String networkName = container.getNetworkName();
		boolean networkAvailable = useBluetooth || networkName != null;
		continueButton.setEnabled(localAuthorId != null && networkAvailable);
	}

	public void onItemSelected(AdapterView<?> parent, View view, int position,
			long id) {
		LocalAuthorItem item = adapter.getItem(position);
		if(item == NEW) {
			container.setLocalAuthorId(null);
			Intent i = new Intent(container, CreateIdentityActivity.class);
			container.startActivity(i);
		} else {
			container.setLocalAuthorId(item.getLocalAuthor().getId());
		}
		enableOrDisableContinueButton();
	}

	public void onNothingSelected(AdapterView<?> parent) {
		container.setLocalAuthorId(null);
	}

	public void onClick(View view) {
		container.setView(new InvitationCodeView(container));
	}
}
