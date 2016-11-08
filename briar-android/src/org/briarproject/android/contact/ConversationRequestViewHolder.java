package org.briarproject.android.contact;

import android.support.annotation.UiThread;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import org.briarproject.R;
import org.briarproject.android.contact.ConversationAdapter.ConversationListener;
import org.briarproject.api.nullsafety.NotNullByDefault;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

@UiThread
@NotNullByDefault
class ConversationRequestViewHolder extends ConversationNoticeInViewHolder {

	private final Button acceptButton;
	private final Button declineButton;

	ConversationRequestViewHolder(View v) {
		super(v);
		acceptButton = (Button) v.findViewById(R.id.acceptButton);
		declineButton = (Button) v.findViewById(R.id.declineButton);
	}

	void bind(ConversationItem conversationItem,
			final ConversationListener listener) {
		super.bind(conversationItem);

		final ConversationRequestItem item =
				(ConversationRequestItem) conversationItem;

		if (item.wasAnswered()) {
			acceptButton.setVisibility(GONE);
			declineButton.setVisibility(GONE);
		} else {
			acceptButton.setVisibility(VISIBLE);
			acceptButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					item.setAnswered(true);
					listener.respondToRequest(item, true);
				}
			});
			declineButton.setVisibility(VISIBLE);
			declineButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					item.setAnswered(true);
					listener.respondToRequest(item, false);
				}
			});
		}
	}

}
