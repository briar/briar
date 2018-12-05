package org.briarproject.briar.android.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.widget.AppCompatImageButton;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.vanniktech.emoji.EmojiEditText;
import com.vanniktech.emoji.EmojiPopup;
import com.vanniktech.emoji.RecentEmoji;

import org.briarproject.briar.R;
import org.briarproject.briar.android.BriarApplication;

import javax.inject.Inject;

import static android.content.Context.INPUT_METHOD_SERVICE;
import static android.content.Context.LAYOUT_INFLATER_SERVICE;
import static android.view.KeyEvent.KEYCODE_ENTER;
import static android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT;
import static java.util.Objects.requireNonNull;
import static org.briarproject.bramble.util.StringUtils.utf8IsTooLong;

public class EmojiTextInputView extends KeyboardAwareLinearLayout implements
		TextWatcher {

	@Inject
	RecentEmoji recentEmoji;

	private final AppCompatImageButton emojiToggle;
	private final EmojiPopup emojiPopup;
	private final EditText editText;

	@Nullable
	private TextInputListener listener;
	private int maxLength = Integer.MAX_VALUE;
	private boolean emptyTextAllowed = false;
	private boolean isEmpty = true;

	public EmojiTextInputView(Context context) {
		this(context, null);
	}

	public EmojiTextInputView(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public EmojiTextInputView(Context context, @Nullable AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);

		// inflate layout
		LayoutInflater inflater = (LayoutInflater) requireNonNull(
				context.getSystemService(LAYOUT_INFLATER_SERVICE));
		inflater.inflate(R.layout.emoji_text_input_view, this, true);

		// get attributes
		TypedArray a = context.obtainStyledAttributes(attrs,
				R.styleable.EmojiTextInputView);
		int paddingBottom = a.getDimensionPixelSize(
				R.styleable.EmojiTextInputView_textPaddingBottom, 0);
		int paddingEnd = a.getDimensionPixelSize(
				R.styleable.EmojiTextInputView_textPaddingEnd, 0);
		int maxLines =
				a.getInteger(R.styleable.EmojiTextInputView_maxTextLines, 0);
		a.recycle();

		// apply attributes to editText
		editText = findViewById(R.id.input_text);
		editText.setPadding(0, 0, paddingEnd, paddingBottom);
		if (maxLines > 0) editText.setMaxLines(maxLines);
		editText.setOnClickListener(v -> showSoftKeyboard());
		editText.addTextChangedListener(this);
		// support sending with Ctrl+Enter
		editText.setOnKeyListener((v, keyCode, event) -> {
			if (listener != null && keyCode == KEYCODE_ENTER &&
					event.isCtrlPressed()) {
				listener.onSendEvent();
				return true;
			}
			return false;
		});
		emojiToggle = findViewById(R.id.emoji_toggle);

		// stuff we can't do in edit mode goes below
		if (isInEditMode()) {
			emojiPopup = null;
			return;
		}
		BriarApplication app =
				(BriarApplication) context.getApplicationContext();
		app.getApplicationComponent().inject(this);
		emojiPopup = EmojiPopup.Builder
				.fromRootView(this)
				.setRecentEmoji(recentEmoji)
				.setOnEmojiPopupShownListener(this::showKeyboardIcon)
				.setOnEmojiPopupDismissListener(this::showEmojiIcon)
				.build((EmojiEditText) editText);
		emojiToggle.setOnClickListener(v -> emojiPopup.toggle());
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

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		editText.setEnabled(enabled);
		emojiToggle.setEnabled(enabled);
	}

	@Override
	public void setGravity(int gravity) {
		editText.setGravity(gravity);
	}

	@Override
	public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
		return editText.requestFocus(direction, previouslyFocusedRect);
	}

	@Override
	public void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		if (emojiPopup.isShowing()) emojiPopup.dismiss();
	}

	void setTextInputListener(@Nullable TextInputListener listener) {
		this.listener = listener;
	}

	void setAllowEmptyText(boolean emptyTextAllowed) {
		this.emptyTextAllowed = emptyTextAllowed;
	}

	void setMaxLength(int maxLength) {
		this.maxLength = maxLength;
	}

	void setMaxLines(int maxLines) {
		editText.setMaxLines(maxLines);
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

	boolean isEmpty() {
		return getText() == null;
	}

	boolean isTooLong() {
		return editText.getText() != null &&
				utf8IsTooLong(editText.getText().toString().trim(), maxLength);
	}

	CharSequence getHint() {
		return editText.getHint();
	}

	void setHint(@StringRes int res) {
		setHint(getContext().getString(res));
	}

	void setHint(CharSequence hint) {
		editText.setHint(hint);
	}

	private void showEmojiIcon() {
		emojiToggle.setImageResource(R.drawable.ic_emoji_toggle);
	}

	private void showKeyboardIcon() {
		emojiToggle.setImageResource(R.drawable.ic_keyboard);
	}

	void showSoftKeyboard() {
		Object o = getContext().getSystemService(INPUT_METHOD_SERVICE);
		InputMethodManager imm = (InputMethodManager) requireNonNull(o);
		imm.showSoftInput(editText, SHOW_IMPLICIT);
	}

	void hideSoftKeyboard() {
		if (emojiPopup.isShowing()) emojiPopup.dismiss();
		IBinder token = editText.getWindowToken();
		Object o = getContext().getSystemService(INPUT_METHOD_SERVICE);
		InputMethodManager imm = (InputMethodManager) requireNonNull(o);
		imm.hideSoftInputFromWindow(token, 0);
	}

	interface TextInputListener {
		void onTextIsEmptyChanged(boolean isEmpty);
		void onSendEvent();
	}

}
