package org.briarproject.briar.android.login;

import android.content.Context;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;

import androidx.appcompat.app.AlertDialog;

import static org.briarproject.briar.android.util.UiUtils.getDialogIcon;

@NotNullByDefault
class LoginUtils {

	static AlertDialog createKeyStrengthenerErrorDialog(Context ctx) {
		AlertDialog.Builder builder =
				new AlertDialog.Builder(ctx, R.style.BriarDialogTheme);
		builder.setIcon(getDialogIcon(ctx, R.drawable.alerts_and_states_error));
		builder.setTitle(R.string.dialog_title_cannot_check_password);
		builder.setMessage(R.string.dialog_message_cannot_check_password);
		builder.setPositiveButton(R.string.ok, null);
		return builder.create();
	}
}
