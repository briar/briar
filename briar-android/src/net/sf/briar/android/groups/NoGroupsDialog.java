package net.sf.briar.android.groups;

import net.sf.briar.R;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

public class NoGroupsDialog extends DialogFragment {

	private Listener listener = null;
	private boolean restricted = false;

	void setListener(Listener listener) {
		this.listener = listener;
	}

	void setRestricted(boolean restricted) {
		this.restricted = restricted;
	}

	@Override
	public Dialog onCreateDialog(Bundle state) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setMessage(restricted ? R.string.no_blogs : R.string.no_groups);
		builder.setPositiveButton(R.string.create_button,
				new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				listener.createGroupButtonClicked();
			}
		});
		builder.setNegativeButton(R.string.cancel_button,
				new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				listener.cancelButtonClicked();
			}
		});
		return builder.create();
	}

	interface Listener {

		void createGroupButtonClicked();

		void cancelButtonClicked();
	}
}
