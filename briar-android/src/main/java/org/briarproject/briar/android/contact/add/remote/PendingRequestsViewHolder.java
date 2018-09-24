package org.briarproject.briar.android.contact.add.remote;

import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.View;
import android.widget.TextView;

import org.briarproject.bramble.api.contact.PendingContact;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.view.TextAvatarView;

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
		avatar.setText(item.getAlias());
		avatar.setBackgroundBytes(toUtf8(item.getAlias() + item.getTimestamp()));
		name.setText(item.getAlias());
		time.setText(formatDate(time.getContext(), item.getTimestamp()));

		int color = ContextCompat
				.getColor(status.getContext(), R.color.briar_green);
		switch (item.getState()) {
			case WAITING_FOR_CONNECTION:
				color = ContextCompat
						.getColor(status.getContext(), R.color.briar_yellow);
				status.setText(R.string.waiting_for_contact_to_come_online);
				break;
			case CONNECTED:
				status.setText(R.string.connecting);
				break;
			case ADDING_CONTACT:
				status.setText(R.string.adding_contact);
				break;
			case FAILED:
				// TODO add remove button
				color = ContextCompat
						.getColor(status.getContext(), R.color.briar_red);
				status.setText(R.string.adding_contact_failed);
				break;
			default:
				throw new IllegalStateException();
		}
		status.setTextColor(color);
	}

}
