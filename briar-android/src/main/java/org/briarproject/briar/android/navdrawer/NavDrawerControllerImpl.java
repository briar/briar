package org.briarproject.briar.android.navdrawer;

import android.app.Activity;
import android.content.Context;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.bramble.api.plugin.Plugin.State;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.event.TransportStateEvent;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.briar.android.controller.DbControllerImpl;
import org.briarproject.briar.android.controller.handler.ResultHandler;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.Plugin.State.DISABLED;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.briar.android.TestingConstants.EXPIRY_DATE;
import static org.briarproject.briar.android.TestingConstants.IS_DEBUG_BUILD;
import static org.briarproject.briar.android.controller.BriarControllerImpl.DOZE_ASK_AGAIN;
import static org.briarproject.briar.android.settings.SettingsFragment.SETTINGS_NAMESPACE;
import static org.briarproject.briar.android.util.UiUtils.needsDozeWhitelisting;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class NavDrawerControllerImpl extends DbControllerImpl
		implements NavDrawerController, EventListener {

	private static final Logger LOG =
			getLogger(NavDrawerControllerImpl.class.getName());

	private static final String EXPIRY_DATE_WARNING = "expiryDateWarning";

	private final PluginManager pluginManager;
	private final SettingsManager settingsManager;
	private final EventBus eventBus;

	// UI thread
	private TransportStateListener listener;

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
		if (e instanceof TransportStateEvent) {
			TransportStateEvent t = (TransportStateEvent) e;
			TransportId id = t.getTransportId();
			State state = t.getState();
			if (LOG.isLoggable(INFO)) {
				LOG.info("TransportStateEvent: " + id + " is " + state);
			}
			listener.stateUpdate(id, state);
		}
	}

	@Override
	public void showExpiryWarning(ResultHandler<Boolean> handler) {
		if (!IS_DEBUG_BUILD) {
			handler.onResult(false);
			return;
		}
		runOnDbThread(() -> {
			try {
				Settings settings =
						settingsManager.getSettings(SETTINGS_NAMESPACE);
				int warningInt = settings.getInt(EXPIRY_DATE_WARNING, 0);

				if (warningInt == 0) {
					// we have not warned before
					handler.onResult(true);
				} else {
					long warningLong = warningInt * 1000L;
					long now = System.currentTimeMillis();
					long daysSinceLastWarning =
							(now - warningLong) / DAYS.toMillis(1);
					long daysBeforeExpiry =
							(EXPIRY_DATE - now) / DAYS.toMillis(1);

					if (daysSinceLastWarning >= 30) {
						handler.onResult(true);
					} else if (daysBeforeExpiry <= 3 &&
							daysSinceLastWarning > 0) {
						handler.onResult(true);
					} else {
						handler.onResult(false);
					}
				}
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	@Override
	public void expiryWarningDismissed() {
		runOnDbThread(() -> {
			try {
				Settings settings = new Settings();
				int date = (int) (System.currentTimeMillis() / 1000L);
				settings.putInt(EXPIRY_DATE_WARNING, date);
				settingsManager.mergeSettings(settings, SETTINGS_NAMESPACE);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	@Override
	public void shouldAskForDozeWhitelisting(Context ctx,
			ResultHandler<Boolean> handler) {
		// check this first, to hit the DbThread only when really necessary
		if (!needsDozeWhitelisting(ctx)) {
			handler.onResult(false);
			return;
		}
		runOnDbThread(() -> {
			try {
				Settings settings =
						settingsManager.getSettings(SETTINGS_NAMESPACE);
				boolean ask = settings.getBoolean(DOZE_ASK_AGAIN, true);
				handler.onResult(ask);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				handler.onResult(true);
			}
		});
	}

	@Override
	public State getTransportState(TransportId transportId) {
		Plugin plugin = pluginManager.getPlugin(transportId);
		return plugin == null ? DISABLED : plugin.getState();
	}

}
