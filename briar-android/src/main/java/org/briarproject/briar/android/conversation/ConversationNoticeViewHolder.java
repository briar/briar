package org.briarproject.briar.android.conversation;

import android.support.annotation.UiThread;
import android.view.View;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static org.briarproject.bramble.util.StringUtils.isNullOrEmpty;
import static org.briarproject.bramble.util.StringUtils.trim;

@UiThread
@NotNullByDefault
class ConversationNoticeViewHolder<T extends ConversationNoticeItem>
		extends ConversationItemViewHolder<T> {

	private final TextView msgText;

	ConversationNoticeViewHolder(View v, boolean isIncoming) {
		super(v, isIncoming);
		msgText = v.findViewById(R.id.msgText);
	}

	@Override
	void bind(T item) {
		super.bind(item);

		String text = item.getMsgText();
		if (isNullOrEmpty(text)) {
			msgText.setVisibility(GONE);
			layout.setBackgroundResource(isIncoming() ? R.drawable.notice_in :
					R.drawable.notice_out);
		} else {
			msgText.setVisibility(VISIBLE);
			msgText.setText(trim(text));
			layout.setBackgroundResource(isIncoming() ?
					R.drawable.notice_in_bottom : R.drawable.notice_out_bottom);
		}
	}

}
