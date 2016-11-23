package org.briarproject.briar.android.sharing;

import android.app.Activity;
import android.support.annotation.CallSuper;

import org.briarproject.bramble.api.contact.event.ContactRemovedEvent;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.event.GroupAddedEvent;
import org.briarproject.bramble.api.sync.event.GroupRemovedEvent;
import org.briarproject.briar.android.controller.DbControllerImpl;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;
import org.briarproject.briar.api.sharing.InvitationItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public abstract class InvitationControllerImpl<I extends InvitationItem>
		extends DbControllerImpl
		implements InvitationController<I>, EventListener {

	protected static final Logger LOG =
			Logger.getLogger(InvitationControllerImpl.class.getName());

	private final EventBus eventBus;
	protected InvitationListener listener;

	public InvitationControllerImpl(@DatabaseExecutor Executor dbExecutor,
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
			if (cId.equals(getShareableClientId())) {
				LOG.info("Group added, reloading");
				listener.loadInvitations(false);
			}
		} else if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent g = (GroupRemovedEvent) e;
			ClientId cId = g.getGroup().getClientId();
			if (cId.equals(getShareableClientId())) {
				LOG.info("Group removed, reloading");
				listener.loadInvitations(false);
			}
		}
	}

	protected abstract ClientId getShareableClientId();

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
