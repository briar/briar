package org.briarproject.android.invitation;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.identity.CreateIdentityActivity;
import org.briarproject.android.identity.LocalAuthorItem;
import org.briarproject.android.identity.LocalAuthorItemComparator;
import org.briarproject.android.identity.LocalAuthorSpinnerAdapter;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.LocalAuthor;

import java.util.Collection;

import javax.inject.Inject;

import static android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE;
import static android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION;
import static org.briarproject.android.identity.LocalAuthorItem.NEW;
import static org.briarproject.android.invitation.AddContactActivity.REQUEST_BLUETOOTH;
import static org.briarproject.android.invitation.AddContactActivity.REQUEST_CREATE_IDENTITY;

class ChooseIdentityView extends AddContactView
implements OnItemSelectedListener, OnClickListener {

	@Inject protected CryptoComponent crypto;
	private LocalAuthorSpinnerAdapter adapter = null;
	private Spinner spinner = null;

	ChooseIdentityView(Context ctx) {
		super(ctx);
	}

	void populate() {
		removeAllViews();
		Context ctx = getContext();
		// TODO
//		RoboGuice.injectMembers(ctx, this);

		LayoutInflater inflater = (LayoutInflater) ctx.getSystemService
				(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.invitation_bluetooth_start, this);

		// current step
		// TODO this could go into the ActionBar eventually
		TextView step = (TextView) view.findViewById(R.id.stepView);
		step.setText(String.format(ctx.getString(R.string.step), 1, 3));

		adapter = new LocalAuthorSpinnerAdapter(ctx, crypto, false);
		spinner = (Spinner) view.findViewById(R.id.spinner);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(this);

		Button continueButton = (Button) view.findViewById(R.id.continueButton);
		continueButton.setOnClickListener(this);

		container.loadLocalAuthors();
	}

	// FIXME: The interaction between views and the container is horrible
	void displayLocalAuthors(Collection<LocalAuthor> authors) {
		adapter.clear();
		for (LocalAuthor a : authors) adapter.add(new LocalAuthorItem(a));
		adapter.sort(LocalAuthorItemComparator.INSTANCE);
		// If a local author has been selected, select it again
		AuthorId localAuthorId = container.getLocalAuthorId();
		if (localAuthorId == null) return;
		int count = adapter.getCount();
		for (int i = 0; i < count; i++) {
			LocalAuthorItem item = adapter.getItem(i);
			if (item == NEW) continue;
			if (item.getLocalAuthor().getId().equals(localAuthorId)) {
				spinner.setSelection(i);
				return;
			}
		}
	}

	public void onItemSelected(AdapterView<?> parent, View view, int position,
			long id) {
		LocalAuthorItem item = adapter.getItem(position);
		if (item == NEW) {
			container.setLocalAuthorId(null);
			Intent i = new Intent(container, CreateIdentityActivity.class);
			container.startActivityForResult(i, REQUEST_CREATE_IDENTITY);
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
		container.startActivityForResult(i, REQUEST_BLUETOOTH);
	}
}
