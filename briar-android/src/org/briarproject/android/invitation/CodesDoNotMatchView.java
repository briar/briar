package org.briarproject.android.invitation;

import static android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE;
import static android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION;
import static android.view.Gravity.CENTER;
import static org.briarproject.android.invitation.AddContactActivity.REQUEST_BLUETOOTH;
import static org.briarproject.android.util.CommonLayoutParams.WRAP_WRAP;

import org.briarproject.R;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

class CodesDoNotMatchView extends AddContactView implements OnClickListener {

	CodesDoNotMatchView(Context ctx) {
		super(ctx);
	}

	void populate() {
		removeAllViews();
		Context ctx = getContext();
		LinearLayout innerLayout = new LinearLayout(ctx);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);

		ImageView icon = new ImageView(ctx);
		icon.setImageResource(R.drawable.alerts_and_states_error);
		innerLayout.addView(icon);

		TextView failed = new TextView(ctx);
		failed.setTextSize(22);
		failed.setPadding(pad, pad, pad, pad);
		failed.setText(R.string.codes_do_not_match);
		innerLayout.addView(failed);
		addView(innerLayout);

		TextView interfering = new TextView(ctx);
		interfering.setGravity(CENTER);
		interfering.setPadding(pad, 0, pad, pad);
		interfering.setText(R.string.interfering);
		addView(interfering);

		Button tryAgainButton = new Button(ctx);
		tryAgainButton.setLayoutParams(WRAP_WRAP);
		tryAgainButton.setText(R.string.try_again_button);
		tryAgainButton.setOnClickListener(this);
		addView(tryAgainButton);
	}

	public void onClick(View view) {
		Intent i = new Intent(ACTION_REQUEST_DISCOVERABLE);
		i.putExtra(EXTRA_DISCOVERABLE_DURATION, 120);
		container.startActivityForResult(i, REQUEST_BLUETOOTH);
	}
}
