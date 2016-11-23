package org.briarproject.briar.android.contact;

import android.support.annotation.UiThread;
import android.view.View;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.util.StringUtils;
import org.briarproject.briar.R;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

@UiThread
@NotNullByDefault
class ConversationNoticeInViewHolder extends ConversationItemViewHolder {

	private final TextView msgText;

	ConversationNoticeInViewHolder(View v) {
		super(v);
		msgText = (TextView) v.findViewById(R.id.msgText);
	}

	@Override
	void bind(ConversationItem conversationItem) {
		super.bind(conversationItem);

		ConversationNoticeInItem item =
				(ConversationNoticeInItem) conversationItem;

		String message = item.getMsgText();
		if (StringUtils.isNullOrEmpty(message)) {
			msgText.setVisibility(GONE);
			layout.setBackgroundResource(R.drawable.notice_in);
		} else {
			msgText.setVisibility(VISIBLE);
			msgText.setText(StringUtils.trim(message));
			layout.setBackgroundResource(R.drawable.notice_in_bottom);
		}
	}

}
