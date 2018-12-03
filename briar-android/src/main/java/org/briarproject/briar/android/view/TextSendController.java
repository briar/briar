package org.briarproject.briar.android.view;

import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.view.View;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.view.TextInputView.SendListener;
import org.briarproject.briar.android.view.TextInputView.TextValidityListener;

import static java.util.Collections.emptyList;

@UiThread
@NotNullByDefault
class TextSendController implements TextValidityListener {

	protected final TextInputController textInput;
	protected final View sendButton;
	@Nullable
	protected SendListener listener;
	protected boolean enabled = true;

	private final boolean allowEmptyText;
	private boolean wasEmpty = true;

	TextSendController(View sendButton, TextInputController textInput,
			boolean allowEmptyText) {
		this.sendButton = sendButton;
		this.sendButton.setOnClickListener(v -> onSendButtonClicked());
		this.sendButton.setEnabled(allowEmptyText);
		this.textInput = textInput;
		this.allowEmptyText = allowEmptyText;
	}

	@Override
	public void onTextValidityChanged(boolean isEmpty) {
		sendButton.setEnabled(enabled && !isEmpty);
		wasEmpty = isEmpty;
	}

	public void setEnabled(boolean enabled) {
		sendButton.setOnClickListener(
				enabled ? v -> onSendButtonClicked() : null);
		sendButton.setEnabled(!wasEmpty || allowEmptyText);
		this.enabled = enabled;
	}

	void setSendListener(SendListener listener) {
		this.listener = listener;
	}

	void onSendButtonClicked() {
		if (listener != null) {
			if (textInput.isTooLong()) {
				textInput.showError();
				return;
			}
			listener.onSendClick(textInput.getText(), emptyList());
		}
	}

}
