package org.briarproject.android.controller;

import android.app.Activity;

import org.briarproject.android.api.ReferenceManager;
import org.briarproject.android.controller.handler.UiResultHandler;
import org.briarproject.api.TransportId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.TransportDisabledEvent;
import org.briarproject.api.event.TransportEnabledEvent;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.plugins.Plugin;
import org.briarproject.api.plugins.PluginManager;

import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

public class NavDrawerControllerImpl extends DBControllerImpl
		implements NavDrawerController, EventListener {

	private static final Logger LOG =
			Logger.getLogger(NavDrawerControllerImpl.class.getName());

	@Inject
	protected ReferenceManager referenceManager;
	@Inject
	protected PluginManager pluginManager;
	@Inject
	protected EventBus eventBus;
	@Inject
	protected Activity activity;

	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile IdentityManager identityManager;

	private TransportStateListener transportStateListener;

	@Inject
	public NavDrawerControllerImpl() {

	}

	@Override
	public void onActivityCreate() {

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
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (transportStateListener != null) {
					transportStateListener.stateUpdate(id, enabled);
				}
			}
		});
	}

	@Override
	public void setTransportListener(TransportStateListener transportListener) {
		this.transportStateListener = transportListener;
	}

	@Override
	public boolean isTransportRunning(TransportId transportId) {
		Plugin plugin = pluginManager.getPlugin(transportId);
		return plugin != null && plugin.isRunning();
	}

	@Override
	public void storeLocalAuthor(final LocalAuthor author,
			final UiResultHandler<Void> resultHandler) {
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
