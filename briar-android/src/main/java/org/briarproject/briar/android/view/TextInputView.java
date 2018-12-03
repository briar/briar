package org.briarproject.briar.android.view;

import android.animation.LayoutTransition;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Parcelable;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.support.v7.widget.AppCompatImageButton;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import com.vanniktech.emoji.EmojiEditText;
import com.vanniktech.emoji.RecentEmoji;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.BriarApplication;
import org.thoughtcrime.securesms.components.KeyboardAwareLinearLayout;

import java.util.List;

import javax.inject.Inject;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;
import static android.view.KeyEvent.KEYCODE_ENTER;
import static java.util.Objects.requireNonNull;

@UiThread
@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class TextInputView extends KeyboardAwareLinearLayout {

	@Inject
	RecentEmoji recentEmoji;

	TextInputController textInputController;
	TextSendController textSendController;
	EmojiEditText editText;
	View sendButton;

	@Nullable
	TextAttachmentController attachmentController;

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
		// get attributes
		TypedArray attributes = context.obtainStyledAttributes(attrs,
				R.styleable.TextInputView);
		String hint = attributes.getString(R.styleable.TextInputView_hint);
		boolean allowEmptyText = attributes
				.getBoolean(R.styleable.TextInputView_allowEmptyText, false);
		boolean supportsAttachments = attributes
				.getBoolean(R.styleable.TextInputView_supportsAttachments, false);
		attributes.recycle();

		// set up input controller
		AppCompatImageButton emojiToggle = findViewById(R.id.emoji_toggle);
		editText = findViewById(R.id.input_text);
		textInputController = new TextInputController(this, emojiToggle,
				editText, recentEmoji, allowEmptyText);
		if (hint != null) textInputController.setHint(hint);

		// set up sending controller
		sendButton = findViewById(R.id.btn_send);
		if (supportsAttachments) {
			textSendController = new TextAttachmentController(this, sendButton,
					textInputController);
		} else {
			textSendController = new TextSendController(sendButton,
					textInputController, allowEmptyText);
		}
		textInputController.setTextValidityListener(textSendController);

		// support sending with Ctrl+Enter
		editText.setOnKeyListener((v, keyCode, event) -> {
			if (keyCode == KEYCODE_ENTER && event.isCtrlPressed()) {
				textSendController.onSendButtonClicked();
				return true;
			}
			return false;
		});
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

	public void setListener(SendListener listener) {
		textSendController.setSendListener(listener);
	}

	public void setAttachImageListener(AttachImageListener imageListener,
			WindowManager windowManager) {
		attachmentController = (TextAttachmentController) textSendController;
		attachmentController.setAttachImageListener(imageListener, windowManager);
	}

	public void onImageReceived(@Nullable Intent resultData) {
		if (attachmentController == null) throw new IllegalStateException();
		attachmentController.onImageReceived(resultData);
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		textInputController.setEnabled(enabled);
		textSendController.setEnabled(enabled);
	}

	@Override
	public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
		return textInputController
				.requestFocus(direction, previouslyFocusedRect);
	}

	@Override
	public void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		textInputController.onDetachedFromWindow();
	}

	public void clearText() {
		textInputController.clearText();
	}

	public void setHint(@StringRes int res) {
		textInputController.setHint(getContext().getString(res));
	}

	public void setMaxTextLength(int maxLength) {
		textInputController.setMaxLength(maxLength);
	}

	public void showSoftKeyboard() {
		textInputController.showSoftKeyboard();
	}

	public void hideSoftKeyboard() {
		textInputController.hideSoftKeyboard();
	}

	interface TextValidityListener {
		void onTextValidityChanged(boolean isEmpty);
	}

	public interface AttachImageListener {
		void onAttachImage(Intent intent);
	}

	public interface SendListener {
		void onSendClick(@Nullable String text, List<Uri> imageUris);
	}

}
