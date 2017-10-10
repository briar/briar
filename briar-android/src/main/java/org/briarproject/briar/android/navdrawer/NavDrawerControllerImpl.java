package org.briarproject.briar.android.navdrawer;

import android.app.Activity;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.event.TransportDisabledEvent;
import org.briarproject.bramble.api.plugin.event.TransportEnabledEvent;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.briar.android.controller.DbControllerImpl;
import org.briarproject.briar.android.controller.handler.ResultHandler;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.briar.BuildConfig.VERSION_CODE;
import static org.briarproject.briar.android.BriarApplication.EXPIRY_DATE;
import static org.briarproject.briar.android.navdrawer.NavDrawerController.ExpiryWarning.NO;
import static org.briarproject.briar.android.navdrawer.NavDrawerController.ExpiryWarning.SHOW;
import static org.briarproject.briar.android.navdrawer.NavDrawerController.ExpiryWarning.UPDATE;
import static org.briarproject.briar.android.settings.SettingsFragment.SETTINGS_NAMESPACE;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class NavDrawerControllerImpl extends DbControllerImpl
		implements NavDrawerController, EventListener {

	private static final Logger LOG =
			Logger.getLogger(NavDrawerControllerImpl.class.getName());
	private static final String EXPIRY_DATE_WARNING = "expiryDateWarning";
	private static final String EXPIRY_DATE_VERSION = "expiryDateVersion";

	private final PluginManager pluginManager;
	private final SettingsManager settingsManager;
	private final EventBus eventBus;

	private volatile TransportStateListener listener;

	@Inject
	NavDrawerControllerImpl(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, PluginManager pluginManager,
			SettingsManager settingsManager, EventBus eventBus) {
		super(dbExecutor, lifecycleManager);
		this.pluginManager = pluginManager;
		this.settingsManager = settingsManager;
		this.eventBus = eventBus;
	}

	@Override
	public void onActivityCreate(Activity activity) {
		listener = (TransportStateListener) activity;
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
	public void showExpiryWarning(final ResultHandler<ExpiryWarning> handler) {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					Settings settings =
							settingsManager.getSettings(SETTINGS_NAMESPACE);
					int warningInt = settings.getInt(EXPIRY_DATE_WARNING, 0);
					int versionCode = settings.getInt(EXPIRY_DATE_VERSION, 0);

					if (warningInt == 0) {
						// we have not warned before
						handler.onResult(SHOW);
					} else {
						long warningLong = warningInt * 1000L;
						long now = System.currentTimeMillis();
						long daysSinceLastWarning =
								(now - warningLong) / 1000 / 60 / 60 / 24;
						long daysBeforeExpiry =
								(EXPIRY_DATE - now) / 1000 / 60 / 60 / 24;

						if (versionCode < 1611) {
							handler.onResult(UPDATE);
						} else if (daysSinceLastWarning >= 30) {
							handler.onResult(SHOW);
						} else if (daysBeforeExpiry <= 3 &&
								daysSinceLastWarning > 0) {
							handler.onResult(SHOW);
						} else handler.onResult(NO);
					}
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	@Override
	public void expiryWarningDismissed() {
		runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					Settings settings = new Settings();
					int date = (int) (System.currentTimeMillis() / 1000L);
					settings.putInt(EXPIRY_DATE_WARNING, date);
					settings.putInt(EXPIRY_DATE_VERSION, VERSION_CODE);
					settingsManager.mergeSettings(settings, SETTINGS_NAMESPACE);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	@Override
	public boolean isTransportRunning(TransportId transportId) {
		Plugin plugin = pluginManager.getPlugin(transportId);

		return plugin != null && plugin.isRunning();
	}

}
