package org.briarproject.android.sharing;

import android.app.Activity;
import android.support.annotation.CallSuper;

import org.briarproject.android.controller.DbControllerImpl;
import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.ContactRemovedEvent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.GroupAddedEvent;
import org.briarproject.api.event.GroupRemovedEvent;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.sharing.InvitationItem;
import org.briarproject.api.sync.ClientId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

public abstract class InvitationsControllerImpl<I extends InvitationItem>
		extends DbControllerImpl
		implements InvitationsController<I>, EventListener {

	protected static final Logger LOG =
			Logger.getLogger(InvitationsControllerImpl.class.getName());

	private final EventBus eventBus;
	protected InvitationListener listener;

	public InvitationsControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, EventBus eventBus) {
		super(dbExecutor, lifecycleManager);
		this.eventBus = eventBus;
	}

	@Override
	public void onActivityCreate(Activity activity) {
		listener = (InvitationListener) activity;
	}

	@Override
	public void onActivityStart() {
		eventBus.addListener(this);
	}

	@Override
	public void onActivityStop() {
		eventBus.removeListener(this);
	}

	@Override
	public void onActivityDestroy() {

	}

	@CallSuper
	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ContactRemovedEvent) {
			LOG.info("Contact removed, reloading...");
			listener.loadInvitations(true);
		} else if (e instanceof GroupAddedEvent) {
			GroupAddedEvent g = (GroupAddedEvent) e;
			ClientId cId = g.getGroup().getClientId();
			if (cId.equals(getClientId())) {
				LOG.info("Group added, reloading");
				listener.loadInvitations(false);
			}
		} else if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent g = (GroupRemovedEvent) e;
			ClientId cId = g.getGroup().getClientId();
			if (cId.equals(getClientId())) {
				LOG.info("Group removed, reloading");
				listener.loadInvitations(true);
			}
		}
	}

	protected abstract ClientId getClientId();

	@Override
	public void loadInvitations(final boolean clear,
			final ResultExceptionHandler<Collection<I>, DbException> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				Collection<I> invitations = new ArrayList<>();
				try {
					long now = System.currentTimeMillis();
					invitations.addAll(getInvitations());
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info(
								"Loading invitations took " + duration + " ms");
					handler.onResult(invitations);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					handler.onException(e);
				}
			}
		});
	}

	@DatabaseExecutor
	protected abstract Collection<I> getInvitations() throws DbException;

}
