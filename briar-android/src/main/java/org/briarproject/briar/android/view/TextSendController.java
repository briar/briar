package org.briarproject.briar.android.view;

import android.os.Parcelable;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.design.widget.Snackbar;
import android.view.View;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.view.TextInputView.SendListener;
import org.briarproject.briar.android.view.TextInputView.TextValidityListener;

import static android.support.design.widget.Snackbar.LENGTH_SHORT;
import static java.util.Collections.emptyList;

@UiThread
@NotNullByDefault
public class TextSendController implements TextValidityListener {

	protected final TextInputController textInput;
	protected final View sendButton;
	protected final SendListener listener;
	protected boolean enabled = true;

	private final boolean allowEmptyText;
	private boolean wasEmpty = true;

	public TextSendController(TextInputView v, SendListener listener,
			boolean allowEmptyText) {
		this.sendButton = v.findViewById(R.id.btn_send);
		this.sendButton.setOnClickListener(view -> onSendButtonClicked());
		this.sendButton.setEnabled(allowEmptyText);
		this.listener = listener;
		this.textInput = v.getTextInputController();
		this.allowEmptyText = allowEmptyText;
	}

	@Override
	public void onTextIsEmptyChanged(boolean isEmpty) {
		sendButton.setEnabled(enabled && !isEmpty);
		wasEmpty = isEmpty;
	}

	@Nullable
	public Parcelable onSaveInstanceState(@Nullable Parcelable superState) {
		return superState;
	}

	@Nullable
	public Parcelable onRestoreInstanceState(Parcelable state) {
		return state;
	}

	@CallSuper
	public void setEnabled(boolean enabled) {
		sendButton.setEnabled(enabled && (!wasEmpty || allowEmptyText));
		this.enabled = enabled;
	}

	void onSendButtonClicked() {
		if (canSend()) {
			listener.onSendClick(textInput.getText(), emptyList());
		}
	}

	protected boolean canSend() {
		if (textInput.isTooLong()) {
			Snackbar.make(sendButton, R.string.text_too_long, LENGTH_SHORT)
					.show();
			return false;
		}
		return enabled && (allowEmptyText || !textInput.isEmpty());
	}

}
