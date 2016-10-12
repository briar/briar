package org.briarproject.android.sharing;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.v7.widget.LinearLayoutManager;
import android.widget.Toast;

import org.briarproject.R;
import org.briarproject.android.BriarActivity;
import org.briarproject.android.view.BriarRecyclerView;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.sharing.InvitationItem;

import java.util.Collection;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.widget.Toast.LENGTH_SHORT;
import static org.briarproject.android.sharing.InvitationAdapter.AvailableForumClickListener;

abstract class InvitationsActivity extends BriarActivity
		implements EventListener, AvailableForumClickListener {

	protected static final Logger LOG =
			Logger.getLogger(InvitationsActivity.class.getName());

	private InvitationAdapter adapter;
	private BriarRecyclerView list;

	@Inject
	EventBus eventBus;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.list);

		adapter = getAdapter(this, this);

		list = (BriarRecyclerView) findViewById(R.id.list);
		if (list != null) {
			list.setLayoutManager(new LinearLayoutManager(this));
			list.setAdapter(adapter);
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		eventBus.addListener(this);
		loadInvitations(false);
	}

	@Override
	public void onStop() {
		super.onStop();
		eventBus.removeListener(this);
		adapter.clear();
		list.showProgressBar();
	}

	@Override
	@CallSuper
	public void eventOccurred(Event e) {
		if (e instanceof ContactRemovedEvent) {
			LOG.info("Contact removed, reloading...");
			loadInvitations(true);
		}
	}

	@Override
	public void onItemClick(InvitationItem item, boolean accept) {
		respondToInvitation(item, accept);

		// show toast
		int res = getDeclineRes();
		if (accept) res = getAcceptRes();
		Toast.makeText(this, res, LENGTH_SHORT).show();

		// remove item and finish if it was the last
		adapter.remove(item);
		if (adapter.getItemCount() == 0) {
			supportFinishAfterTransition();
		}
	}

	abstract protected InvitationAdapter getAdapter(Context ctx,
			AvailableForumClickListener listener);

	abstract protected void loadInvitations(boolean clear);

	abstract protected void respondToInvitation(final InvitationItem item,
			final boolean accept);

	abstract protected int getAcceptRes();

	abstract protected int getDeclineRes();

	protected void displayInvitations(
			final Collection<InvitationItem> invitations, final boolean clear) {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				if (invitations.isEmpty()) {
					LOG.info("No more invitations available, finishing");
					finish();
				} else {
					if (clear) adapter.setItems(invitations);
					else adapter.addAll(invitations);
				}
			}
		});
	}

}
