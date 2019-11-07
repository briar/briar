package org.briarproject.briar.android.util;

import android.view.View;
import android.view.View.OnClickListener;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.snackbar.Snackbar.Callback;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import static android.os.Build.VERSION.SDK_INT;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static androidx.core.content.ContextCompat.getColor;
import static com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE;

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
		// Workaround for https://issuetracker.google.com/issues/64285517
		if (duration == LENGTH_INDEFINITE && SDK_INT < 21) {
			// Hide snackbar while it's opening to make bouncing less noticeable
			s.getView().setVisibility(INVISIBLE);
			s.addCallback(new Callback() {
				@Override
				public void onShown(Snackbar snackbar) {
					snackbar.getView().setVisibility(VISIBLE);
					// Request layout again in case snackbar is in wrong place
					snackbar.getView().requestLayout();
				}

				@Override
				public void onDismissed(Snackbar snackbar, int event) {
					snackbar.getView().setVisibility(INVISIBLE);
				}
			});
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
