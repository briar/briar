package org.briarproject.briar.android.contact;

import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.view.TextAvatarView;
import org.briarproject.briar.api.messaging.MessagingManager.PendingContact;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.briarproject.bramble.util.StringUtils.toUtf8;
import static org.briarproject.briar.android.util.UiUtils.formatDate;

@NotNullByDefault
public class PendingRequestsViewHolder extends ViewHolder {

	private final TextAvatarView avatar;
	private final TextView name;
	private final TextView time;
	private final TextView status;

	public PendingRequestsViewHolder(View v) {
		super(v);
		avatar = v.findViewById(R.id.avatar);
		name = v.findViewById(R.id.name);
		time = v.findViewById(R.id.time);
		status = v.findViewById(R.id.status);
	}

	public void bind(PendingContact item) {
		avatar.setText(item.getName());
		avatar.setBackgroundBytes(toUtf8(item.getName() + item.getTimestamp()));
		name.setText(item.getName());
		time.setText(formatDate(time.getContext(), item.getTimestamp()));
		long diff = item.getAddAt() - System.currentTimeMillis();
		Log.e("TEST", "diff: " + diff);
		int color = ContextCompat
				.getColor(status.getContext(), R.color.briar_green);
		if (diff < SECONDS.toMillis(10)) {
			status.setText(R.string.adding_contact);
		} else if (diff < SECONDS.toMillis(20)) {
			status.setText(R.string.connecting);
		} else if (diff < SECONDS.toMillis(30)) {
			status.setText(R.string.waiting_for_contact_to_come_online);
			color = ContextCompat
					.getColor(status.getContext(), R.color.briar_yellow);
		}
		status.setTextColor(color);
	}

}
