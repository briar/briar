package org.briarproject.briar.android.view;

import android.animation.LayoutTransition;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Parcelable;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.view.KeyboardAwareLinearLayout.OnKeyboardShownListener;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;
import static java.util.Objects.requireNonNull;

@UiThread
@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class TextInputView extends LinearLayout {

	@Nullable
	TextSendController textSendController;
	final EmojiTextInputView textInput;

	public TextInputView(Context context) {
		this(context, null);
	}

	public TextInputView(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public TextInputView(Context context, @Nullable AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		setSaveEnabled(true);
		setOrientation(VERTICAL);
		setLayoutTransition(new LayoutTransition());

		// inflate layout
		LayoutInflater inflater = (LayoutInflater) requireNonNull(
				context.getSystemService(LAYOUT_INFLATER_SERVICE));
		inflater.inflate(getLayout(), this, true);

		// get attributes
		TypedArray attributes = context.obtainStyledAttributes(attrs,
				R.styleable.TextInputView);
		String hint = attributes.getString(R.styleable.TextInputView_hint);
		boolean allowEmptyText = attributes
				.getBoolean(R.styleable.TextInputView_allowEmptyText, false);
		attributes.recycle();

		textInput = findViewById(R.id.emojiTextInput);
		textInput.setAllowEmptyText(allowEmptyText);
		if (hint != null) textInput.setHint(hint);
	}

	@LayoutRes
	protected int getLayout() {
		return R.layout.text_input_view;
	}

	@Nullable
	@Override
	protected Parcelable onSaveInstanceState() {
		Parcelable superState = super.onSaveInstanceState();
		if (textSendController != null) {
			superState = textSendController.onSaveInstanceState(superState);
		}
		return superState;
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		if (textSendController != null) {
			Parcelable outState =
					textSendController.onRestoreInstanceState(state);
			super.onRestoreInstanceState(outState);
		} else {
			super.onRestoreInstanceState(state);
		}
	}

	/**
	 * Call this in onCreate() before any other methods of this class.
	 */
	public <T extends TextSendController> void setSendController(T controller) {
		textSendController = controller;
		textInput.setTextInputListener(textSendController);
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		textInput.setEnabled(enabled);
		requireNonNull(textSendController).setEnabled(enabled);
	}

	@Override
	public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
		return textInput.requestFocus(direction, previouslyFocusedRect);
	}

	EmojiTextInputView getEmojiTextInputView() {
		return textInput;
	}

	public void clearText() {
		textInput.clearText();
	}

	public void setHint(@StringRes int res) {
		textInput.setHint(getContext().getString(res));
	}

	public void setMaxTextLength(int maxLength) {
		textInput.setMaxLength(maxLength);
	}

	public boolean isKeyboardOpen() {
		return textInput.isKeyboardOpen();
	}

	public void showSoftKeyboard() {
		textInput.showSoftKeyboard();
	}

	public void hideSoftKeyboard() {
		textInput.hideSoftKeyboard();
	}

	public void addOnKeyboardShownListener(OnKeyboardShownListener listener) {
		textInput.addOnKeyboardShownListener(listener);
	}

	public void removeOnKeyboardShownListener(
			OnKeyboardShownListener listener) {
		textInput.removeOnKeyboardShownListener(listener);
	}

}
