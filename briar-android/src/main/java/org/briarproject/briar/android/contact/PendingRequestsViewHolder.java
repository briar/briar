package org.briarproject.briar.android.contact;

import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.View;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;

import static org.briarproject.briar.android.util.UiUtils.formatDate;

@NotNullByDefault
public class PendingRequestsViewHolder extends ViewHolder {

	private final TextView name;
	private final TextView time;

	public PendingRequestsViewHolder(View v) {
		super(v);
		name = v.findViewById(R.id.name);
		time = v.findViewById(R.id.time);
	}

	public void bind(PendingRequestsItem item) {
		name.setText(item.getName());
		time.setText(formatDate(time.getContext(), item.getTimestamp()));
	}

}
