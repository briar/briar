package org.briarproject.briar.android.contact.add.remote;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.bramble.api.contact.PendingContact;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.util.BriarAdapter;

@NotNullByDefault
class PendingContactListAdapter extends
		BriarAdapter<PendingContact, PendingContactViewHolder> {

	private final PendingContactListener listener;

	PendingContactListAdapter(Context ctx, PendingContactListener listener,
			Class<PendingContact> c) {
		super(ctx, c);
		this.listener = listener;
	}

	@Override
	public PendingContactViewHolder onCreateViewHolder(ViewGroup viewGroup,
			int i) {
		View v = LayoutInflater.from(viewGroup.getContext()).inflate(
				R.layout.list_item_pending_contact, viewGroup, false);
		return new PendingContactViewHolder(v, listener);
	}

	@Override
	public void onBindViewHolder(
			PendingContactViewHolder pendingContactViewHolder, int i) {
		pendingContactViewHolder.bind(items.get(i));
	}

	@Override
	public int compare(PendingContact item1, PendingContact item2) {
		return (int) (item1.getTimestamp() - item2.getTimestamp());
	}

	@Override
	public boolean areContentsTheSame(PendingContact item1,
			PendingContact item2) {
		return item1.getId().equals(item2.getId()) &&
				item1.getAlias().equals(item2.getAlias()) &&
				item1.getTimestamp() == item2.getTimestamp() &&
				item1.getState() == item2.getState();
	}

	@Override
	public boolean areItemsTheSame(PendingContact item1,
			PendingContact item2) {
		return item1.getId().equals(item2.getId());
	}

}
