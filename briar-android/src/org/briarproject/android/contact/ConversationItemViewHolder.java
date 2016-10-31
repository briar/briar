package org.briarproject.android.contact;

import android.support.annotation.CallSuper;
import android.support.annotation.UiThread;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.util.AndroidUtils;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.util.StringUtils;

@UiThread
@NotNullByDefault
class ConversationItemViewHolder extends ViewHolder {

	protected final ViewGroup layout;
	private final TextView text;
	private final TextView time;

	ConversationItemViewHolder(View v) {
		super(v);
		layout = (ViewGroup) v.findViewById(R.id.layout);
		text = (TextView) v.findViewById(R.id.text);
		time = (TextView) v.findViewById(R.id.time);
	}

	@CallSuper
	void bind(ConversationItem item) {
		if (item.getBody() == null) {
			text.setText("\u2026");
		} else {
			text.setText(StringUtils.trim(item.getBody()));
		}

		long timestamp = item.getTime();
		time.setText(AndroidUtils.formatDate(time.getContext(), timestamp));
	}

}
