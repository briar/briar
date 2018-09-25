package org.briarproject.briar.android.contact;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.util.BriarAdapter;

@NotNullByDefault
public class PendingRequestsAdapter extends
		BriarAdapter<PendingRequestsItem, PendingRequestsViewHolder> {

	public PendingRequestsAdapter(Context ctx, Class<PendingRequestsItem> c) {
		super(ctx, c);
	}

	@NonNull
	@Override
	public PendingRequestsViewHolder onCreateViewHolder(
			ViewGroup viewGroup, int i) {
		View v = LayoutInflater.from(viewGroup.getContext()).inflate(
				R.layout.list_item_pending_contact, viewGroup, false);
		return new PendingRequestsViewHolder(v);
	}

	@Override
	public void onBindViewHolder(
			PendingRequestsViewHolder pendingRequestsViewHolder, int i) {
		pendingRequestsViewHolder.bind(items.get(i));
	}

	@Override
	public int compare(PendingRequestsItem item1, PendingRequestsItem item2) {
		return (int) (item1.getTimestamp() - item2.getTimestamp());
	}

	@Override
	public boolean areContentsTheSame(PendingRequestsItem item1,
			PendingRequestsItem item2) {
		return item1.getName().equals(item2.getName()) &&
				item1.getTimestamp() == item2.getTimestamp();
	}

	@Override
	public boolean areItemsTheSame(PendingRequestsItem item1,
			PendingRequestsItem item2) {
		return item1.getName().equals(item2.getName()) &&
				item1.getTimestamp() == item2.getTimestamp();
	}

}
