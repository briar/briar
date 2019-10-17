package org.briarproject.briar.android.conversation;

import android.view.View;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import static org.briarproject.bramble.util.StringUtils.trim;
import static org.briarproject.briar.android.util.UiUtils.formatDate;

@UiThread
@NotNullByDefault
abstract class ConversationItemViewHolder extends ViewHolder {

	protected final ConversationListener listener;
	protected final ConstraintLayout layout;
	@Nullable
	private final OutItemViewHolder outViewHolder;
	private final TextView text;
	protected final TextView time;

	ConversationItemViewHolder(View v, ConversationListener listener,
			boolean isIncoming) {
		super(v);
		this.listener = listener;
		this.outViewHolder = isIncoming ? null : new OutItemViewHolder(v);
		layout = v.findViewById(R.id.layout);
		text = v.findViewById(R.id.text);
		time = v.findViewById(R.id.time);
	}

	@CallSuper
	void bind(ConversationItem item) {
		if (item.getText() != null) {
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
