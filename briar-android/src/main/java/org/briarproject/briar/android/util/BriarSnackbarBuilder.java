package org.briarproject.briar.android.util;

import android.support.annotation.ColorRes;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.view.View;
import android.view.View.OnClickListener;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;

import static android.support.v4.content.ContextCompat.getColor;

@NotNullByDefault
public class BriarSnackbarBuilder {

	@ColorRes
	private int backgroundResId = R.color.briar_primary;
	@StringRes
	private int actionResId;
	@Nullable
	private OnClickListener onClickListener;

	public Snackbar make(View view, CharSequence text, int duration) {
		Snackbar s = Snackbar.make(view, text, duration);
		s.getView().setBackgroundResource(backgroundResId);
		if (onClickListener != null) {
			s.setActionTextColor(getColor(view.getContext(),
					R.color.briar_button_text_positive));
			s.setAction(actionResId, onClickListener);
		}
		return s;
	}

	public Snackbar make(View view, @StringRes int resId, int duration) {
		return make(view, view.getResources().getText(resId), duration);
	}

	public BriarSnackbarBuilder setBackgroundColor(
			@ColorRes int backgroundResId) {
		this.backgroundResId = backgroundResId;
		return this;
	}

	public BriarSnackbarBuilder setAction(@StringRes int actionResId,
			OnClickListener onClickListener) {
		this.actionResId = actionResId;
		this.onClickListener = onClickListener;
		return this;
	}

}
