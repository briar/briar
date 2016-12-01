package org.briarproject.briar.android.contact;

import android.support.annotation.UiThread;
import android.view.View;
import android.widget.ImageView;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;

@UiThread
@NotNullByDefault
abstract class ConversationOutItemViewHolder
		extends ConversationItemViewHolder {

	private final ImageView status;

	ConversationOutItemViewHolder(View v) {
		super(v);
		status = (ImageView) v.findViewById(R.id.status);
	}

	@Override
	void bind(ConversationItem conversationItem) {
		super.bind(conversationItem);

		ConversationOutItem item = (ConversationOutItem) conversationItem;

		int res;
		if (item.isSeen()) {
			if (hasDarkBackground()) res = R.drawable.message_delivered_white;
			else res = R.drawable.message_delivered;
		} else if (item.isSent()) {
			if (hasDarkBackground()) res = R.drawable.message_sent_white;
			else res = R.drawable.message_sent;
		} else {
			if (hasDarkBackground()) res = R.drawable.message_stored_white;
			else res = R.drawable.message_stored;
		}
		status.setImageResource(res);
	}

	protected abstract boolean hasDarkBackground();

}
