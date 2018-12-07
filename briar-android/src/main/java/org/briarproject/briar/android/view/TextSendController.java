package org.briarproject.briar.android.view;

import android.net.Uri;
import android.os.Parcelable;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.design.widget.Snackbar;
import android.view.View;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.view.EmojiTextInputView.TextInputListener;

import java.util.List;

import static android.support.design.widget.Snackbar.LENGTH_SHORT;
import static java.util.Collections.emptyList;

@UiThread
@NotNullByDefault
public class TextSendController implements TextInputListener {

	protected final EmojiTextInputView textInput;
	protected final View sendButton;
	protected final SendListener listener;
	protected boolean enabled = true;
	protected final boolean allowEmptyText;

	private boolean wasEmpty = true;

	public TextSendController(TextInputView v, SendListener listener,
			boolean allowEmptyText) {
		this.sendButton = v.findViewById(R.id.btn_send);
		this.sendButton.setOnClickListener(view -> onSendEvent());
		this.sendButton.setEnabled(allowEmptyText);
		this.listener = listener;
		this.textInput = v.getEmojiTextInputView();
		this.allowEmptyText = allowEmptyText;
	}

	@Override
	public void onTextIsEmptyChanged(boolean isEmpty) {
		sendButton.setEnabled(enabled && (!isEmpty || canSendEmptyText()));
		wasEmpty = isEmpty;
	}

	@Override
	public void onSendEvent() {
		if (canSend()) {
			listener.onSendClick(textInput.getText(), emptyList());
		}
	}

	protected final boolean canSend() {
		if (textInput.isTooLong()) {
			Snackbar.make(sendButton, R.string.text_too_long, LENGTH_SHORT)
					.show();
			return false;
		}
		return enabled && (canSendEmptyText() || !textInput.isEmpty());
	}

	protected boolean canSendEmptyText() {
		return allowEmptyText;
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
		sendButton.setEnabled(enabled && (!wasEmpty || canSendEmptyText()));
		this.enabled = enabled;
	}

	public interface SendListener {
		void onSendClick(@Nullable String text, List<Uri> imageUris);
	}

}
