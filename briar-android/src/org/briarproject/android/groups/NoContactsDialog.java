package org.briarproject.android.groups;

import org.briarproject.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;

public class NoContactsDialog {

	private Listener listener = null;

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	public Dialog build(Context ctx) {
		if (listener == null) throw new IllegalStateException();
		AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
		builder.setMessage(R.string.no_contacts_prompt);
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
