package org.briarproject.briar.android.conversation;

import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;

import static org.briarproject.bramble.util.StringUtils.trim;
import static org.briarproject.briar.android.util.UiUtils.formatDate;

@UiThread
@NotNullByDefault
abstract class ConversationItemViewHolder extends ViewHolder {

	protected final ViewGroup layout;
	@Nullable
	private final OutItemViewHolder outViewHolder;
	private final TextView text;
	private final TextView time;

	ConversationItemViewHolder(View v, boolean isIncoming) {
		super(v);
		this.outViewHolder = isIncoming ? null : new OutItemViewHolder(v);
		layout = v.findViewById(R.id.layout);
		text = v.findViewById(R.id.text);
		time = v.findViewById(R.id.time);
	}

	@CallSuper
	void bind(ConversationItem item, ConversationListener listener) {
		if (item.getText() == null) {
			text.setText("\u2026");
		} else {
			text.setText(trim(item.getText()));
		}

		long timestamp = item.getTime();
		time.setText(formatDate(time.getContext(), timestamp));

		if (outViewHolder != null) outViewHolder.bind(item);
	}

	boolean isIncoming() {
		return outViewHolder == null;
	}

}
