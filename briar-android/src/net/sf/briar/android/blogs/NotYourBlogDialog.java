package net.sf.briar.android.blogs;

import net.sf.briar.R;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

public class NotYourBlogDialog extends DialogFragment {

	private static final DialogInterface.OnClickListener IGNORE =
			new DialogInterface.OnClickListener() {
		public void onClick(DialogInterface dialog, int which) {}
	};

	@Override
	public Dialog onCreateDialog(Bundle state) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setMessage(R.string.not_your_blog);
		builder.setPositiveButton(R.string.ok_button, IGNORE);
		return builder.create();
	}
}
