package org.briarproject.briar.android.contact;

import android.support.annotation.UiThread;
import android.view.View;
import android.widget.Button;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.contact.ConversationAdapter.ConversationListener;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

@UiThread
@NotNullByDefault
class ConversationRequestViewHolder extends ConversationNoticeInViewHolder {

	private final Button acceptButton;
	private final Button declineButton;

	ConversationRequestViewHolder(View v) {
		super(v);
		acceptButton = v.findViewById(R.id.acceptButton);
		declineButton = v.findViewById(R.id.declineButton);
	}

	void bind(ConversationItem conversationItem,
			ConversationListener listener) {
		super.bind(conversationItem);

		ConversationRequestItem item =
				(ConversationRequestItem) conversationItem;

		if (item.wasAnswered() && item.canBeOpened()) {
			acceptButton.setVisibility(VISIBLE);
			acceptButton.setText(R.string.open);
			acceptButton.setOnClickListener(
					v -> listener.openRequestedShareable(item));
			declineButton.setVisibility(GONE);
		} else if (item.wasAnswered()) {
			acceptButton.setVisibility(GONE);
			declineButton.setVisibility(GONE);
		} else {
			acceptButton.setVisibility(VISIBLE);
			acceptButton.setText(R.string.accept);
			acceptButton.setOnClickListener(v -> {
				acceptButton.setEnabled(false);
				declineButton.setEnabled(false);
				listener.respondToRequest(item, true);
			});
			declineButton.setVisibility(VISIBLE);
			declineButton.setOnClickListener(v -> {
				acceptButton.setEnabled(false);
				declineButton.setEnabled(false);
				listener.respondToRequest(item, false);
			});
		}
	}

}
