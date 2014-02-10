package org.briarproject.android.invitation;

import static android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE;
import static android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION;
import static android.view.Gravity.CENTER;
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

class ConnectionFailedView extends AddContactView implements OnClickListener {

	private Button tryAgainButton = null;

	ConnectionFailedView(Context ctx) {
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
		failed.setText(R.string.connection_failed);
		innerLayout.addView(failed);
		addView(innerLayout);

		TextView couldNotFind = new TextView(ctx);
		couldNotFind.setGravity(CENTER);
		couldNotFind.setTextSize(14);
		couldNotFind.setPadding(pad, 0, pad, pad);
		couldNotFind.setText(R.string.could_not_find_contact);
		addView(couldNotFind);

		tryAgainButton = new Button(ctx);
		tryAgainButton.setLayoutParams(WRAP_WRAP);
		tryAgainButton.setText(R.string.try_again_button);
		tryAgainButton.setOnClickListener(this);
		addView(tryAgainButton);
	}

	public void onClick(View view) {
		Intent i = new Intent(ACTION_REQUEST_DISCOVERABLE);
		i.putExtra(EXTRA_DISCOVERABLE_DURATION, 120);
		container.startActivityForResult(i, 0);
	}
}
