package org.briarproject.briar.android.view;

import android.content.Context;
import android.graphics.Rect;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.support.v7.widget.AppCompatImageButton;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.vanniktech.emoji.EmojiEditText;
import com.vanniktech.emoji.EmojiPopup;
import com.vanniktech.emoji.RecentEmoji;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;

import static android.content.Context.INPUT_METHOD_SERVICE;
import static android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT;
import static java.util.Objects.requireNonNull;
import static org.briarproject.bramble.util.StringUtils.utf8IsTooLong;
import static org.briarproject.briar.android.view.TextInputView.TextValidityListener;

@UiThread
@NotNullByDefault
class TextInputController implements TextWatcher {

	private final Context ctx;
	private final AppCompatImageButton emojiToggle;
	private final EmojiPopup emojiPopup;
	private final EmojiEditText editText;

	@Nullable
	private TextValidityListener listener;
	private int maxLength = Integer.MAX_VALUE;
	private final boolean emptyTextAllowed;
	private boolean isEmpty = true;

	TextInputController(View rootView, AppCompatImageButton emojiToggle,
			EmojiEditText editText, RecentEmoji recentEmoji,
			boolean emptyTextAllowed) {
		ctx = rootView.getContext();
		this.emojiToggle = emojiToggle;
		this.editText = editText;
		this.editText.addTextChangedListener(this);
		this.editText.setOnClickListener(v -> showSoftKeyboard());
		emojiPopup = EmojiPopup.Builder
				.fromRootView(rootView)
				.setRecentEmoji(recentEmoji)
				.setOnEmojiPopupShownListener(this::showKeyboardIcon)
				.setOnEmojiPopupDismissListener(this::showEmojiIcon)
				.build(this.editText);
		this.emojiToggle.setOnClickListener(v -> emojiPopup.toggle());
		this.emptyTextAllowed = emptyTextAllowed;
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before,
			int count) {
		// Need to start at position 0 to change empty
		if (start != 0 || emptyTextAllowed || listener == null) return;
		if (s.length() == 0) {
			if (!isEmpty) {
				isEmpty = true;
				listener.onTextIsEmptyChanged(true);
			}
		} else if (isEmpty) {
			isEmpty = false;
			listener.onTextIsEmptyChanged(false);
		}
	}

	@Override
	public void afterTextChanged(Editable s) {
	}

	void setMaxLength(int maxLength) {
		this.maxLength = maxLength;
	}

	boolean isEmpty() {
		return getText() == null;
	}

	boolean isTooLong() {
		return editText.getText() != null &&
				utf8IsTooLong(editText.getText().toString().trim(), maxLength);
	}

	/**
	 * Returns the current text or {@code null},
	 * if it is empty or only consists of white-spaces.
	 */
	@Nullable
	String getText() {
		Editable editable = editText.getText();
		String str = editable == null ? null : editable.toString().trim();
		if (str == null || str.length() == 0) return null;
		return str;
	}

	void clearText() {
		editText.setText(null);
	}

	CharSequence getHint() {
		return editText.getHint();
	}

	void setHint(@StringRes int res) {
		setHint(ctx.getString(res));
	}

	void setHint(CharSequence hint) {
		editText.setHint(hint);
	}

	void setTextValidityListener(@Nullable TextValidityListener listener) {
		this.listener = listener;
	}

	boolean requestFocus(int direction, Rect previouslyFocusedRect) {
		return editText.requestFocus(direction, previouslyFocusedRect);
	}

	void onDetachedFromWindow() {
		if (emojiPopup.isShowing()) emojiPopup.dismiss();
	}

	void showSoftKeyboard() {
		Object o = ctx.getSystemService(INPUT_METHOD_SERVICE);
		InputMethodManager imm = (InputMethodManager) requireNonNull(o);
		imm.showSoftInput(editText, SHOW_IMPLICIT);
	}

	void hideSoftKeyboard() {
		if (emojiPopup.isShowing()) emojiPopup.dismiss();
		IBinder token = editText.getWindowToken();
		Object o = ctx.getSystemService(INPUT_METHOD_SERVICE);
		InputMethodManager imm = (InputMethodManager) requireNonNull(o);
		imm.hideSoftInputFromWindow(token, 0);
	}

	private void showEmojiIcon() {
		emojiToggle.setImageResource(R.drawable.ic_emoji_toggle);
	}

	private void showKeyboardIcon() {
		emojiToggle.setImageResource(R.drawable.ic_keyboard);
	}

	public void setEnabled(boolean enabled) {
		editText.setEnabled(enabled);
		emojiToggle.setEnabled(enabled);
	}

}
