package org.briarproject.android.controller;

import android.app.Activity;

import org.briarproject.android.api.ReferenceManager;
import org.briarproject.android.controller.handler.ResultHandler;
import org.briarproject.api.TransportId;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.TransportDisabledEvent;
import org.briarproject.api.event.TransportEnabledEvent;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.plugins.Plugin;
import org.briarproject.api.plugins.PluginManager;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

public class NavDrawerControllerImpl extends DbControllerImpl
		implements NavDrawerController, EventListener {

	private static final Logger LOG =
			Logger.getLogger(NavDrawerControllerImpl.class.getName());

	private final ReferenceManager referenceManager;
	private final PluginManager pluginManager;
	private final EventBus eventBus;
	private final IdentityManager identityManager;

	private volatile TransportStateListener listener;

	@Inject
	NavDrawerControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			ReferenceManager referenceManager, PluginManager pluginManager,
			EventBus eventBus, IdentityManager identityManager) {
		super(dbExecutor, lifecycleManager);
		this.referenceManager = referenceManager;
		this.pluginManager = pluginManager;
		this.eventBus = eventBus;
		this.identityManager = identityManager;
	}

	@Override
	public void onActivityCreate(Activity activity) {
		listener = (TransportStateListener) activity;
	}

	@Override
	public void onActivityResume() {
		eventBus.addListener(this);
	}

	@Override
	public void onActivityPause() {
		eventBus.removeListener(this);
	}

	@Override
	public void onActivityDestroy() {

	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof TransportEnabledEvent) {
			TransportId id = ((TransportEnabledEvent) e).getTransportId();
			if (LOG.isLoggable(INFO)) {
				LOG.info("TransportEnabledEvent: " + id.getString());
			}
			transportStateUpdate(id, true);
		} else if (e instanceof TransportDisabledEvent) {
			TransportId id = ((TransportDisabledEvent) e).getTransportId();
			if (LOG.isLoggable(INFO)) {
				LOG.info("TransportDisabledEvent: " + id.getString());
			}
			transportStateUpdate(id, false);
		}
	}

	private void transportStateUpdate(final TransportId id,
			final boolean enabled) {
		listener.runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				listener.stateUpdate(id, enabled);
			}
		});
	}

	@Override
	public boolean isTransportRunning(TransportId transportId) {
		Plugin plugin = pluginManager.getPlugin(transportId);
		return plugin != null && plugin.isRunning();
	}

	@Override
	public void storeLocalAuthor(final LocalAuthor author,
			final ResultHandler<Void> resultHandler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					identityManager.addLocalAuthor(author);
					long duration = System.currentTimeMillis() - now;
					if (LOG.isLoggable(INFO))
						LOG.info("Storing author took " + duration + " ms");
					resultHandler.onResult(null);
				} catch (final DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	@Override
	public LocalAuthor removeAuthorHandle(long handle) {
		return referenceManager.removeReference(handle, LocalAuthor.class);
	}

}
