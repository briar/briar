package org.briarproject.briar.android.view;

import android.animation.LayoutTransition;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.IBinder;
import android.support.annotation.CallSuper;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.support.v7.widget.AppCompatImageButton;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.vanniktech.emoji.EmojiEditText;
import com.vanniktech.emoji.EmojiPopup;
import com.vanniktech.emoji.RecentEmoji;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.BriarApplication;
import org.thoughtcrime.securesms.components.KeyboardAwareLinearLayout;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.content.Context.INPUT_METHOD_SERVICE;
import static android.content.Context.LAYOUT_INFLATER_SERVICE;
import static android.view.KeyEvent.KEYCODE_ENTER;
import static android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT;

@UiThread
@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class TextInputView extends KeyboardAwareLinearLayout {

	@Inject
	RecentEmoji recentEmoji;

	@Nullable
	TextInputListener listener;

	AppCompatImageButton emojiToggle;
	EmojiEditText editText;
	EmojiPopup emojiPopup;
	View sendButton;

	public TextInputView(Context context) {
		this(context, null);
	}

	public TextInputView(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public TextInputView(Context context, @Nullable AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		if (!isInEditMode()) {
			BriarApplication app =
					(BriarApplication) context.getApplicationContext();
			app.getApplicationComponent().inject(this);
		}
		setOrientation(VERTICAL);
		setLayoutTransition(new LayoutTransition());
		inflateLayout(context);
		setUpViews(context, attrs);
	}

	protected void inflateLayout(Context context) {
		LayoutInflater inflater = (LayoutInflater) context
				.getSystemService(LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.text_input_view, this, true);
	}

	@CallSuper
	protected void setUpViews(Context context, @Nullable AttributeSet attrs) {
		emojiToggle = findViewById(R.id.emoji_toggle);
		editText = findViewById(R.id.input_text);
		emojiPopup = EmojiPopup.Builder
				.fromRootView(this)
				.setRecentEmoji(recentEmoji)
				.setOnEmojiPopupShownListener(this::showKeyboardIcon)
				.setOnEmojiPopupDismissListener(this::showEmojiIcon)
				.build(editText);
		sendButton = findViewById(R.id.btn_send);

		// get attributes
		TypedArray attributes = context.obtainStyledAttributes(attrs,
				R.styleable.TextInputView);
		String hint = attributes.getString(R.styleable.TextInputView_hint);
		attributes.recycle();

		if (hint != null) editText.setHint(hint);

		emojiToggle.setOnClickListener(v -> emojiPopup.toggle());
		editText.setOnClickListener(v -> showSoftKeyboard());
		editText.setOnKeyListener((v, keyCode, event) -> {
			if (keyCode == KEYCODE_ENTER && event.isCtrlPressed()) {
				trySendMessage();
				return true;
			}
			return false;
		});
		sendButton.setOnClickListener(v -> trySendMessage());
	}

	private void showEmojiIcon() {
		emojiToggle.setImageResource(R.drawable.ic_emoji_toggle);
	}

	private void showKeyboardIcon() {
		emojiToggle.setImageResource(R.drawable.ic_keyboard);
	}

	private void trySendMessage() {
		if (listener != null) {
			listener.onSendClick(editText.getText().toString());
		}
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

	public void setText(String text) {
		editText.setText(text);
	}

	public Editable getText() {
		return editText.getText();
	}

	public void setHint(@StringRes int res) {
		editText.setHint(res);
	}

	public void setSendButtonEnabled(boolean enabled) {
		sendButton.setEnabled(enabled);
	}

	public void addTextChangedListener(TextWatcher watcher) {
		editText.addTextChangedListener(watcher);
	}

	public void setListener(TextInputListener listener) {
		this.listener = listener;
	}

	public void showSoftKeyboard() {
		Object o = getContext().getSystemService(INPUT_METHOD_SERVICE);
		((InputMethodManager) o).showSoftInput(editText, SHOW_IMPLICIT);
	}

	public void hideSoftKeyboard() {
		if (emojiPopup.isShowing()) emojiPopup.dismiss();
		IBinder token = editText.getWindowToken();
		Object o = getContext().getSystemService(INPUT_METHOD_SERVICE);
		((InputMethodManager) o).hideSoftInputFromWindow(token, 0);
	}

	public interface TextInputListener {
		void onSendClick(String text);
	}

}
