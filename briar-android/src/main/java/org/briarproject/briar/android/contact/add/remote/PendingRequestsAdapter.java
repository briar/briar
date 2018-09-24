package org.briarproject.briar.android.contact.add.remote;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.PendingContact;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.util.BriarAdapter;

@NotNullByDefault
public class PendingRequestsAdapter extends
		BriarAdapter<PendingContact, PendingRequestsViewHolder> {

	public PendingRequestsAdapter(Context ctx, Class<PendingContact> c) {
		super(ctx, c);
	}

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
	public int compare(PendingContact item1, PendingContact item2) {
		return (int) (item1.getTimestamp() - item2.getTimestamp());
	}

	@Override
	public boolean areContentsTheSame(PendingContact item1,
			PendingContact item2) {
		return item1.getAlias().equals(item2.getAlias()) &&
				item1.getTimestamp() == item2.getTimestamp();
	}

	@Override
	public boolean areItemsTheSame(PendingContact item1,
			PendingContact item2) {
		return item1.getAlias().equals(item2.getAlias()) &&
				item1.getTimestamp() == item2.getTimestamp();
	}

	// TODO use PendingContactId
	public void remove(Contact contact) {
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).getAlias().equals(contact.getAuthor().getName())) {
				items.removeItemAt(i);
				return;
			}
		}
	}

}
