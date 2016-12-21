package org.briarproject.briar.android.invitation;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import org.briarproject.briar.R;

import static android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE;
import static android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_BLUETOOTH;

class ChooseIdentityView extends AddContactView implements OnClickListener {

	ChooseIdentityView(Context ctx) {
		super(ctx);
	}

	@Override
	void populate() {
		removeAllViews();
		Context ctx = getContext();

		LayoutInflater inflater = (LayoutInflater) ctx.getSystemService
				(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.invitation_bluetooth_start, this);

		Button continueButton = (Button) view.findViewById(R.id.continueButton);
		continueButton.setOnClickListener(this);

		container.loadLocalAuthor();
	}

	@Override
	public void onClick(View view) {
		Intent i = new Intent(ACTION_REQUEST_DISCOVERABLE);
		i.putExtra(EXTRA_DISCOVERABLE_DURATION, 120);
		container.startActivityForResult(i, REQUEST_BLUETOOTH);
	}
}
