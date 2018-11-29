package org.briarproject.briar.android.view;

import android.animation.LayoutTransition;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.net.Uri;
import android.os.IBinder;
import android.os.Parcelable;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
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

import java.util.List;

import javax.inject.Inject;

import static android.content.Context.INPUT_METHOD_SERVICE;
import static android.content.Context.LAYOUT_INFLATER_SERVICE;
import static android.view.KeyEvent.KEYCODE_ENTER;
import static android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

@UiThread
@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class TextInputView extends KeyboardAwareLinearLayout {

	@Inject
	RecentEmoji recentEmoji;

	@Nullable
	TextInputListener listener;
	@Nullable
	TextInputAttachmentController attachmentController;

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
		setSaveEnabled(true);
		if (!isInEditMode()) setUpViews(context, attrs);
	}

	protected void inflateLayout(Context context) {
		LayoutInflater inflater = (LayoutInflater) requireNonNull(
				context.getSystemService(LAYOUT_INFLATER_SERVICE));
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

		if (hint != null) setHint(hint);

		emojiToggle.setOnClickListener(v -> emojiPopup.toggle());
		editText.setOnClickListener(v -> showSoftKeyboard());
		editText.setOnKeyListener((v, keyCode, event) -> {
			if (keyCode == KEYCODE_ENTER && event.isCtrlPressed()) {
				onSendButtonClicked();
				return true;
			}
			return false;
		});
		sendButton.setOnClickListener(v -> onSendButtonClicked());
	}

	@Nullable
	@Override
	protected Parcelable onSaveInstanceState() {
		Parcelable superState = super.onSaveInstanceState();
		if (attachmentController != null) {
			superState = attachmentController.onSaveInstanceState(superState);
		}
		return superState;
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		if (attachmentController != null) {
			Parcelable outState =
					attachmentController.onRestoreInstanceState(state);
			super.onRestoreInstanceState(outState);
		} else {
			super.onRestoreInstanceState(state);
		}
	}

	public void setListener(TextInputListener listener) {
		this.listener = listener;
	}

	/**
	 * Call this during onCreate() to enable image attachment support.
	 * Do not call it twice!
	 */
	public void setAttachImageListener(AttachImageListener imageListener) {
		if (attachmentController != null) throw new IllegalStateException();
		attachmentController = new TextInputAttachmentController(getRootView(),
				editText, sendButton, imageListener
		);
	}

	private void showEmojiIcon() {
		emojiToggle.setImageResource(R.drawable.ic_emoji_toggle);
	}

	private void showKeyboardIcon() {
		emojiToggle.setImageResource(R.drawable.ic_keyboard);
	}

	private void onSendButtonClicked() {
		if (listener != null) {
			Editable editable = editText.getText();
			String text = editable == null || editable.length() == 0 ?
					null : editable.toString();
			List<Uri> imageUris = attachmentController == null ? emptyList() :
					attachmentController.getUris();
			listener.onSendClick(text, imageUris);
		}
		if (attachmentController != null) {
			attachmentController.reset();
		}
	}

	public void onImageReceived(@Nullable Intent resultData) {
		if (attachmentController == null) throw new IllegalStateException();
		attachmentController.onImageReceived(resultData);
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

	public boolean isEmpty() {
		return editText.getText() == null || editText.getText().length() == 0;
	}

	public void setHint(@StringRes int res) {
		setHint(getContext().getString(res));
	}

	public void setHint(String hint) {
		if (attachmentController != null) attachmentController.saveHint(hint);
		editText.setHint(hint);
	}

	public void setSendButtonEnabled(boolean enabled) {
		sendButton.setEnabled(enabled);
	}

	public void addTextChangedListener(TextWatcher watcher) {
		editText.addTextChangedListener(watcher);
	}

	public void showSoftKeyboard() {
		Object o = getContext().getSystemService(INPUT_METHOD_SERVICE);
		InputMethodManager imm = (InputMethodManager) requireNonNull(o);
		imm.showSoftInput(editText, SHOW_IMPLICIT);
	}

	public void hideSoftKeyboard() {
		if (emojiPopup.isShowing()) emojiPopup.dismiss();
		IBinder token = editText.getWindowToken();
		Object o = getContext().getSystemService(INPUT_METHOD_SERVICE);
		InputMethodManager imm = (InputMethodManager) requireNonNull(o);
		imm.hideSoftInputFromWindow(token, 0);
	}

	public interface AttachImageListener {
		void onAttachImage(Intent intent);
	}

	public interface TextInputListener {
		void onSendClick(@Nullable String text, List<Uri> imageUris);
	}

}
