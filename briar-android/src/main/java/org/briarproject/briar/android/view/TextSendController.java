package org.briarproject.briar.android.view;

import android.content.Context;
import android.os.Parcelable;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.view.EmojiTextInputView.TextInputListener;
import org.briarproject.briar.api.attachment.AttachmentHeader;

import java.util.List;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import static com.google.android.material.snackbar.Snackbar.LENGTH_SHORT;
import static java.util.Collections.emptyList;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;

@UiThread
@NotNullByDefault
public class TextSendController implements TextInputListener {

	protected final EmojiTextInputView textInput;
	protected final View compositeSendButton;
	protected final SendListener listener;

	protected boolean textIsEmpty = true;
	private boolean ready = true;
	private long currentTimer = NO_AUTO_DELETE_TIMER;

	private final CharSequence defaultHint;
	private final boolean allowEmptyText;

	public TextSendController(TextInputView v, SendListener listener,
			boolean allowEmptyText) {
		this.compositeSendButton = v.findViewById(R.id.compositeSendButton);
		this.compositeSendButton.setOnClickListener(view -> onSendEvent());
		this.listener = listener;
		this.textInput = v.getEmojiTextInputView();
		this.defaultHint = textInput.getHint();
		this.allowEmptyText = allowEmptyText;
	}

	@Override
	public void onTextIsEmptyChanged(boolean isEmpty) {
		textIsEmpty = isEmpty;
		updateViewState();
	}

	@Override
	public void onSendEvent() {
		if (canSend()) {
			listener.onSendClick(textInput.getText(), emptyList());
		}
	}

	public void setReady(boolean ready) {
		this.ready = ready;
		updateViewState();
	}

	/**
	 * Sets the current auto delete timer and updates the UI accordingly.
	 */
	public void setAutoDeleteTimer(long timer) {
		currentTimer = timer;
		updateViewState();
	}

	@CallSuper
	protected void updateViewState() {
		textInput.setEnabled(isTextInputEnabled());
		textInput.setHint(getCurrentTextHint());
		compositeSendButton.setEnabled(isSendButtonEnabled());
		if (compositeSendButton instanceof CompositeSendButton) {
			CompositeSendButton sendButton =
					(CompositeSendButton) compositeSendButton;
			sendButton.setBombVisible(isBombVisible());
		}
	}

	protected boolean isTextInputEnabled() {
		return ready;
	}

	protected boolean isSendButtonEnabled() {
		return ready && (!textIsEmpty || canSendEmptyText());
	}

	protected boolean isBombVisible() {
		return currentTimer != NO_AUTO_DELETE_TIMER;
	}

	protected CharSequence getCurrentTextHint() {
		if (currentTimer == NO_AUTO_DELETE_TIMER) {
			return defaultHint;
		} else {
			Context ctx = textInput.getContext();
			return ctx.getString(R.string.message_hint_auto_delete);
		}
	}

	protected final boolean canSend() {
		if (textInput.isTooLong()) {
			Snackbar.make(compositeSendButton, R.string.text_too_long,
					LENGTH_SHORT).show();
			return false;
		}
		return ready && (canSendEmptyText() || !textIsEmpty);
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

	@UiThread
	public interface SendListener {
		void onSendClick(@Nullable String text, List<AttachmentHeader> headers);
	}

}
