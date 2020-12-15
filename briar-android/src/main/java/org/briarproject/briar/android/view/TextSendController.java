package org.briarproject.briar.android.view;

import android.os.Parcelable;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.view.EmojiTextInputView.TextInputListener;
import org.briarproject.briar.api.attachment.AttachmentHeader;

import java.util.List;

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

	protected boolean ready = true, textIsEmpty = true;

	private final boolean allowEmptyText;
	private CharSequence defaultHint;

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

	public void setAutoDeleteTimer(long timer) {
		// update hint
		if (timer == NO_AUTO_DELETE_TIMER) {
			textInput.setHint(defaultHint);
		} else {
			// this might need to be adapted when other screens
			// besides the private conversation use auto delete timers
			defaultHint = textInput.getHint();
			textInput.setHint(R.string.message_hint_auto_delete);
		}
	}

	protected void updateViewState() {
		textInput.setEnabled(ready);
		compositeSendButton
				.setEnabled(ready && (!textIsEmpty || canSendEmptyText()));
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
