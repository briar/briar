package org.briarproject.briar.android.login;

import android.content.Context;
import android.graphics.drawable.Drawable;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;

import androidx.appcompat.app.AlertDialog;

import static androidx.core.content.ContextCompat.getColor;
import static androidx.core.content.ContextCompat.getDrawable;
import static androidx.core.graphics.drawable.DrawableCompat.setTint;
import static java.util.Objects.requireNonNull;

@NotNullByDefault
class LoginUtils {

	static AlertDialog createKeyStrengthenerErrorDialog(Context ctx) {
		AlertDialog.Builder builder =
				new AlertDialog.Builder(ctx, R.style.BriarDialogTheme);
		Drawable icon = getDrawable(ctx, R.drawable.alerts_and_states_error);
		setTint(requireNonNull(icon), getColor(ctx, R.color.color_primary));
		builder.setIcon(icon);
		builder.setTitle(R.string.dialog_title_cannot_check_password);
		builder.setMessage(R.string.dialog_message_cannot_check_password);
		builder.setPositiveButton(R.string.ok, null);
		return builder.create();
	}
}
