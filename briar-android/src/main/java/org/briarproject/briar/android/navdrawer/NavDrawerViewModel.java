package org.briarproject.briar.android.navdrawer;

import android.app.Application;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.BluetoothConstants;
import org.briarproject.bramble.api.plugin.LanTcpConstants;
import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.bramble.api.plugin.Plugin.State;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.event.TransportStateEvent;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.SettingsManager;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.Plugin.PREF_PLUGIN_ENABLE;
import static org.briarproject.bramble.api.plugin.Plugin.State.STARTING_STOPPING;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_MOBILE;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_NETWORK;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_NETWORK_WITH_BRIDGES;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_ONLY_WHEN_CHARGING;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.briar.android.TestingConstants.EXPIRY_DATE;
import static org.briarproject.briar.android.TestingConstants.IS_DEBUG_BUILD;
import static org.briarproject.briar.android.controller.BriarControllerImpl.DOZE_ASK_AGAIN;
import static org.briarproject.briar.android.settings.SettingsFragment.SETTINGS_NAMESPACE;
import static org.briarproject.briar.android.util.UiUtils.needsDozeWhitelisting;

@NotNullByDefault
public class NavDrawerViewModel extends AndroidViewModel
		implements EventListener {

	private static final Logger LOG =
			getLogger(NavDrawerViewModel.class.getName());

	private static final String EXPIRY_DATE_WARNING = "expiryDateWarning";
	static final TransportId[] TRANSPORT_IDS =
			{TorConstants.ID, LanTcpConstants.ID, BluetoothConstants.ID};

	@DatabaseExecutor
	private final Executor dbExecutor;
	private final SettingsManager settingsManager;
	private final PluginManager pluginManager;
	private final EventBus eventBus;

	private final MutableLiveData<Boolean> showExpiryWarning =
			new MutableLiveData<>();
	private final MutableLiveData<Boolean> shouldAskForDozeWhitelisting =
			new MutableLiveData<>();

	private final MutableLiveData<Plugin.State> torPluginState =
			new MutableLiveData<>();
	private final MutableLiveData<Plugin.State> wifiPluginState =
			new MutableLiveData<>();
	private final MutableLiveData<Plugin.State> btPluginState =
			new MutableLiveData<>();

	@Inject
	NavDrawerViewModel(Application app, @DatabaseExecutor Executor dbExecutor,
			SettingsManager settingsManager, PluginManager pluginManager,
			EventBus eventBus) {
		super(app);
		this.dbExecutor = dbExecutor;
		this.settingsManager = settingsManager;
		this.pluginManager = pluginManager;
		this.eventBus = eventBus;
		eventBus.addListener(this);
		updatePluginStates();
	}

	@Override
	protected void onCleared() {
		eventBus.removeListener(this);
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
			MutableLiveData<Plugin.State> liveData = getPluginLiveData(id);
			if (liveData != null) liveData.postValue(state);
		}
	}

	LiveData<Boolean> showExpiryWarning() {
		return showExpiryWarning;
	}

	@UiThread
	void checkExpiryWarning() {
		if (!IS_DEBUG_BUILD) {
			showExpiryWarning.setValue(false);
			return;
		}
		dbExecutor.execute(() -> {
			try {
				Settings settings =
						settingsManager.getSettings(SETTINGS_NAMESPACE);
				int warningInt = settings.getInt(EXPIRY_DATE_WARNING, 0);

				if (warningInt == 0) {
					// we have not warned before
					showExpiryWarning.postValue(true);
				} else {
					long warningLong = warningInt * 1000L;
					long now = System.currentTimeMillis();
					long daysSinceLastWarning =
							(now - warningLong) / DAYS.toMillis(1);
					long daysBeforeExpiry =
							(EXPIRY_DATE - now) / DAYS.toMillis(1);

					if (daysSinceLastWarning >= 30) {
						showExpiryWarning.postValue(true);
					} else if (daysBeforeExpiry <= 3 &&
							daysSinceLastWarning > 0) {
						showExpiryWarning.postValue(true);
					} else {
						showExpiryWarning.postValue(false);
					}
				}
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	@UiThread
	void expiryWarningDismissed() {
		showExpiryWarning.setValue(false);
		dbExecutor.execute(() -> {
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

	LiveData<Boolean> shouldAskForDozeWhitelisting() {
		return shouldAskForDozeWhitelisting;
	}

	@UiThread
	void checkDozeWhitelisting() {
		// check this first, to hit the DbThread only when really necessary
		if (!needsDozeWhitelisting(getApplication())) {
			shouldAskForDozeWhitelisting.setValue(false);
			return;
		}
		dbExecutor.execute(() -> {
			try {
				Settings settings =
						settingsManager.getSettings(SETTINGS_NAMESPACE);
				boolean ask = settings.getBoolean(DOZE_ASK_AGAIN, true);
				shouldAskForDozeWhitelisting.postValue(ask);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				shouldAskForDozeWhitelisting.postValue(true);
			}
		});
	}

	private void updatePluginStates() {
		for (TransportId t : TRANSPORT_IDS) {
			MutableLiveData<Plugin.State> liveData = getPluginLiveData(t);
			if (liveData == null) throw new AssertionError();
			liveData.setValue(getTransportState(t));
		}
	}

	private State getTransportState(TransportId id) {
		Plugin plugin = pluginManager.getPlugin(id);
		return plugin == null ? STARTING_STOPPING : plugin.getState();
	}

	@Nullable
	private MutableLiveData<State> getPluginLiveData(TransportId t) {
		if (t.equals(TorConstants.ID)) {
			return torPluginState;
		} else if (t.equals(LanTcpConstants.ID)) {
			return wifiPluginState;
		} else if (t.equals(BluetoothConstants.ID)) {
			return btPluginState;
		} else {
			return null;
		}
	}

	LiveData<State> getPluginState(TransportId t) {
		LiveData<Plugin.State> liveData = getPluginLiveData(t);
		if (liveData == null) throw new AssertionError();
		return liveData;
	}

	int getReasonsDisabled(TransportId id) {
		Plugin plugin = pluginManager.getPlugin(id);
		return plugin == null ? 0 : plugin.getReasonsDisabled();
	}

	void setPluginEnabled(TransportId t, boolean enabled) {
		pluginManager.setPluginEnabled(t, enabled);
	}

	void setTorEnabled(boolean battery, boolean mobileData, boolean location) {
		Plugin plugin = pluginManager.getPlugin(TorConstants.ID);
		if (plugin == null) return;

		Settings s = new Settings();
		s.putBoolean(PREF_PLUGIN_ENABLE, true);
		if (battery) s.putBoolean(PREF_TOR_ONLY_WHEN_CHARGING, false);
		if (mobileData) s.putBoolean(PREF_TOR_MOBILE, true);
		if (location) s.putInt(PREF_TOR_NETWORK, PREF_TOR_NETWORK_WITH_BRIDGES);
		dbExecutor.execute(() -> {
			try {
				settingsManager.mergeSettings(s, TorConstants.ID.getString());
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

}
