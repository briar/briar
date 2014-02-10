package org.briarproject.android.invitation;

import static android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE;
import static android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION;
import static android.view.Gravity.CENTER;
import static org.briarproject.android.identity.LocalAuthorItem.NEW;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_WRAP;
import static org.briarproject.android.util.CommonLayoutParams.WRAP_WRAP;

import org.briarproject.R;
import org.briarproject.android.identity.CreateIdentityActivity;
import org.briarproject.android.identity.LocalAuthorItem;
import org.briarproject.android.identity.LocalAuthorSpinnerAdapter;

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

class ChooseIdentityView extends AddContactView
implements OnItemSelectedListener, OnClickListener {

	private LocalAuthorSpinnerAdapter adapter = null;
	private Spinner spinner = null;
	private Button continueButton = null;

	ChooseIdentityView(Context ctx) {
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
		yourNickname.setPadding(pad, pad, pad, pad);
		yourNickname.setText(R.string.your_nickname);
		innerLayout.addView(yourNickname);

		adapter = new LocalAuthorSpinnerAdapter(ctx, false);
		spinner = new Spinner(ctx);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(this);
		container.loadLocalAuthors(adapter);
		innerLayout.addView(spinner);
		addView(innerLayout);

		TextView faceToFace = new TextView(ctx);
		faceToFace.setTextSize(14);
		faceToFace.setPadding(pad, pad, pad, pad);
		faceToFace.setText(R.string.face_to_face);
		addView(faceToFace);

		continueButton = new Button(ctx);
		continueButton.setLayoutParams(WRAP_WRAP);
		continueButton.setText(R.string.continue_button);
		continueButton.setOnClickListener(this);
		addView(continueButton);
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
	}

	public void onNothingSelected(AdapterView<?> parent) {
		container.setLocalAuthorId(null);
	}

	public void onClick(View view) {
		Intent i = new Intent(ACTION_REQUEST_DISCOVERABLE);
		i.putExtra(EXTRA_DISCOVERABLE_DURATION, 120);
		container.startActivityForResult(i, 0);
	}
}
