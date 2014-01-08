package org.briarproject.android.groups;

import org.briarproject.R;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

public class NoContactsDialog extends DialogFragment {

	private Listener listener = null;

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	@Override
	public Dialog onCreateDialog(Bundle state) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setMessage(R.string.no_contacts);
		builder.setPositiveButton(R.string.add_button,
				new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				listener.contactCreationSelected();
			}
		});
		builder.setNegativeButton(R.string.cancel_button,
				new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				listener.contactCreationCancelled();
			}
		});
		return builder.create();
	}

	public interface Listener {

		void contactCreationSelected();

		void contactCreationCancelled();
	}
}
