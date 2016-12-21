package org.briarproject.briar.android.invitation;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import org.briarproject.briar.R;

import static android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE;
import static android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_BLUETOOTH;

class ErrorView extends AddContactView implements OnClickListener {

	private final int error;
	private final int explanation;

	ErrorView(Context ctx) {
		super(ctx);
		this.error = R.string.connection_failed;
		this.explanation = R.string.could_not_find_contact;
	}

	ErrorView(Context ctx, int error, int explanation) {
		super(ctx);
		this.error = error;
		this.explanation = explanation;
	}

	@Override
	void populate() {
		removeAllViews();
		Context ctx = getContext();

		LayoutInflater inflater = (LayoutInflater) ctx.getSystemService
				(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.invitation_error, this);

		TextView errorView = (TextView) view.findViewById(R.id.errorTextView);
		errorView.setText(ctx.getString(error));

		TextView explanationView = (TextView) view.findViewById(R.id.explanationTextView);
		explanationView.setText(ctx.getString(explanation));

		Button tryAgainButton = (Button) view.findViewById(R.id.tryAgainButton);
		tryAgainButton.setOnClickListener(this);
	}

	@Override
	public void onClick(View view) {
		Intent i = new Intent(ACTION_REQUEST_DISCOVERABLE);
		i.putExtra(EXTRA_DISCOVERABLE_DURATION, 120);
		container.startActivityForResult(i, REQUEST_BLUETOOTH);
	}
}
