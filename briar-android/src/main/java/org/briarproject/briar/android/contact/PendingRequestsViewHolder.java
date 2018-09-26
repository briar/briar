package org.briarproject.briar.android.contact;

import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.View;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.view.TextAvatarView;
import org.briarproject.briar.api.messaging.MessagingManager.PendingContact;

import static org.briarproject.bramble.util.StringUtils.toUtf8;
import static org.briarproject.briar.android.util.UiUtils.formatDate;

@NotNullByDefault
public class PendingRequestsViewHolder extends ViewHolder {

	private final TextAvatarView avatar;
	private final TextView name;
	private final TextView time;

	public PendingRequestsViewHolder(View v) {
		super(v);
		avatar = v.findViewById(R.id.avatar);
		name = v.findViewById(R.id.name);
		time = v.findViewById(R.id.time);
	}

	public void bind(PendingContact item) {
		avatar.setText(item.getName());
		avatar.setBackgroundBytes(toUtf8(item.getName() + item.getTimestamp()));
		name.setText(item.getName());
		time.setText(formatDate(time.getContext(), item.getTimestamp()));
	}

}
