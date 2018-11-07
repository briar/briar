package org.briarproject.briar.android.conversation;

import android.support.annotation.UiThread;
import android.view.View;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@UiThread
@NotNullByDefault
class ConversationMessageViewHolder
		extends ConversationItemViewHolder<ConversationMessageItem> {

	// image support will be added here (#1242)

	ConversationMessageViewHolder(View v, boolean isIncoming) {
		super(v, isIncoming);
	}

	@Override
	void bind(ConversationMessageItem item) {
		super.bind(item);
	}

}
