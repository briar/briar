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
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.briar.R;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.Plugin.PREF_PLUGIN_ENABLE;
import static org.briarproject.bramble.api.plugin.Plugin.REASON_USER;
import static org.briarproject.bramble.api.plugin.Plugin.State.STARTING_STOPPING;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_MOBILE;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_NETWORK;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_NETWORK_WITH_BRIDGES;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_ONLY_WHEN_CHARGING;
import static org.briarproject.bramble.api.plugin.TorConstants.REASON_BATTERY;
import static org.briarproject.bramble.api.plugin.TorConstants.REASON_COUNTRY_BLOCKED;
import static org.briarproject.bramble.api.plugin.TorConstants.REASON_MOBILE_DATA;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.briar.android.navdrawer.NavDrawerViewModel.TRANSPORT_IDS;
import static org.briarproject.briar.android.util.UiUtils.getCountryDisplayName;
import static org.briarproject.briar.android.util.UiUtils.getDialogIcon;

@NotNullByDefault
public class PluginViewModel extends AndroidViewModel implements EventListener {

	private static final Logger LOG =
			getLogger(PluginViewModel.class.getName());

	private final Application app;
	@DatabaseExecutor
	private final Executor dbExecutor;
	private final SettingsManager settingsManager;
	private final PluginManager pluginManager;
	private final LocationUtils locationUtils;
	private final EventBus eventBus;

	private final MutableLiveData<State> torPluginState =
			new MutableLiveData<>();
	private final MutableLiveData<State> wifiPluginState =
			new MutableLiveData<>();
	private final MutableLiveData<State> btPluginState =
			new MutableLiveData<>();

	@Inject
	PluginViewModel(Application app, @DatabaseExecutor Executor dbExecutor,
			SettingsManager settingsManager, PluginManager pluginManager,
			LocationUtils locationUtils, EventBus eventBus) {
		super(app);
		this.app = app;
		this.dbExecutor = dbExecutor;
		this.settingsManager = settingsManager;
		this.pluginManager = pluginManager;
		this.locationUtils = locationUtils;
		this.eventBus = eventBus;
		eventBus.addListener(this);
		initialisePluginStates();
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
			MutableLiveData<State> liveData = getPluginLiveData(id);
			liveData.postValue(state);
		}
	}

	void onSwitchClicked(TransportId t, boolean isChecked) {
		if (isChecked) tryToEnablePlugin(t);
		else setPluginEnabled(t, false);
	}

	LiveData<State> getPluginState(TransportId t) {
		return getPluginLiveData(t);
	}

	private void tryToEnablePlugin(TransportId id) {
		if (id.equals(TorConstants.ID)) {
			int reasons = getReasonsDisabled(id);
			if (reasons == 0 || reasons == REASON_USER) {
				setPluginEnabled(id, true);
			} else {
				showTorSettingsDialog(reasons);
			}
		} else {
			setPluginEnabled(id, true);
		}
	}

	private void initialisePluginStates() {
		for (TransportId t : TRANSPORT_IDS) {
			MutableLiveData<State> liveData = getPluginLiveData(t);
			liveData.setValue(getTransportState(t));
		}
	}

	private State getTransportState(TransportId id) {
		Plugin plugin = pluginManager.getPlugin(id);
		return plugin == null ? STARTING_STOPPING : plugin.getState();
	}

	private MutableLiveData<State> getPluginLiveData(TransportId t) {
		if (t.equals(TorConstants.ID)) {
			return torPluginState;
		} else if (t.equals(LanTcpConstants.ID)) {
			return wifiPluginState;
		} else if (t.equals(BluetoothConstants.ID)) {
			return btPluginState;
		} else {
			throw new IllegalArgumentException();
		}
	}

	private int getReasonsDisabled(TransportId id) {
		Plugin plugin = pluginManager.getPlugin(id);
		return plugin == null ? 0 : plugin.getReasonsDisabled();
	}

	private void setPluginEnabled(TransportId t, boolean enabled) {
		pluginManager.setPluginEnabled(t, enabled);
	}

	private void setTorEnabled(boolean battery, boolean mobileData,
			boolean location) {
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

	private void showTorSettingsDialog(int reasonsDisabled) {
		boolean battery = (reasonsDisabled & REASON_BATTERY) != 0;
		boolean mobileData = (reasonsDisabled & REASON_MOBILE_DATA) != 0;
		boolean location = (reasonsDisabled & REASON_COUNTRY_BLOCKED) != 0;

		StringBuilder s = new StringBuilder();
		if (location) {
			s.append("\t\u2022 ");
			s.append(app.getString(R.string.tor_override_network_setting,
					getCountryDisplayName(locationUtils.getCurrentCountry())));
			s.append('\n');
		}
		if (mobileData) {
			s.append("\t\u2022 ");
			s.append(app.getString(R.string.tor_override_mobile_data_setting));
			s.append('\n');
		}
		if (battery) {
			s.append("\t\u2022 ");
			s.append(app.getString(R.string.tor_only_when_charging_title));
			s.append('\n');
		}
		String message = app.getString(
				R.string.tor_override_settings_body, s.toString());

		AlertDialog.Builder b =
				new AlertDialog.Builder(app, R.style.BriarDialogTheme);
		b.setTitle(R.string.tor_override_settings_title);
		b.setIcon(getDialogIcon(app, R.drawable.ic_settings_black_24dp));
		b.setMessage(message);
		b.setPositiveButton(R.string.tor_override_settings_confirm,
				(dialog, which) ->
						setTorEnabled(battery, mobileData, location));
		b.setNegativeButton(R.string.cancel, (dialog, which) ->
				dialog.dismiss());
		b.show();
	}
}
