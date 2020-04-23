package org.briarproject.briar.android.navdrawer;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.network.NetworkStatus;
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
import org.briarproject.bramble.api.settings.event.SettingsUpdatedEvent;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED;
import static android.bluetooth.BluetoothAdapter.EXTRA_STATE;
import static android.bluetooth.BluetoothAdapter.STATE_ON;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.Plugin.PREF_PLUGIN_ENABLE;
import static org.briarproject.bramble.api.plugin.Plugin.State.STARTING_STOPPING;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;

@NotNullByDefault
public class PluginViewModel extends AndroidViewModel implements EventListener {

	private static final Logger LOG =
			getLogger(PluginViewModel.class.getName());

	private final Application app;
	private final Executor dbExecutor;
	private final SettingsManager settingsManager;
	private final PluginManager pluginManager;
	private final EventBus eventBus;
	private final BroadcastReceiver receiver;

	private final MutableLiveData<State> torPluginState =
			new MutableLiveData<>();
	private final MutableLiveData<State> wifiPluginState =
			new MutableLiveData<>();
	private final MutableLiveData<State> btPluginState =
			new MutableLiveData<>();

	private final MutableLiveData<Boolean> torEnabledSetting =
			new MutableLiveData<>(false);
	private final MutableLiveData<Boolean> wifiEnabledSetting =
			new MutableLiveData<>(false);
	private final MutableLiveData<Boolean> btEnabledSetting =
			new MutableLiveData<>(false);

	private final MutableLiveData<NetworkStatus> networkStatus =
			new MutableLiveData<>();

	private final MutableLiveData<Boolean> bluetoothTurnedOn =
			new MutableLiveData<>(false);

	@Inject
	PluginViewModel(Application app, @DatabaseExecutor Executor dbExecutor,
			SettingsManager settingsManager, PluginManager pluginManager,
			EventBus eventBus, NetworkManager networkManager) {
		super(app);
		this.app = app;
		this.dbExecutor = dbExecutor;
		this.settingsManager = settingsManager;
		this.pluginManager = pluginManager;
		this.eventBus = eventBus;
		eventBus.addListener(this);
		receiver = new BluetoothStateReceiver();
		app.registerReceiver(receiver, new IntentFilter(ACTION_STATE_CHANGED));
		networkStatus.setValue(networkManager.getNetworkStatus());
		torPluginState.setValue(getTransportState(TorConstants.ID));
		wifiPluginState.setValue(getTransportState(LanTcpConstants.ID));
		btPluginState.setValue(getTransportState(BluetoothConstants.ID));
		loadSettings();
	}

	@Override
	protected void onCleared() {
		eventBus.removeListener(this);
		app.unregisterReceiver(receiver);
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof SettingsUpdatedEvent) {
			SettingsUpdatedEvent s = (SettingsUpdatedEvent) e;
			if (s.getNamespace().equals(TorConstants.ID.getString())) {
				boolean enable =
						s.getSettings().getBoolean(PREF_PLUGIN_ENABLE, true);
				torEnabledSetting.setValue(enable);
			} else if (s.getNamespace()
					.equals(LanTcpConstants.ID.getString())) {
				boolean enable =
						s.getSettings().getBoolean(PREF_PLUGIN_ENABLE, false);
				wifiEnabledSetting.setValue(enable);
			} else if (s.getNamespace().equals(
					BluetoothConstants.ID.getString())) {
				boolean enable =
						s.getSettings().getBoolean(PREF_PLUGIN_ENABLE, false);
				btEnabledSetting.setValue(enable);
			}
		} else if (e instanceof TransportStateEvent) {
			TransportStateEvent t = (TransportStateEvent) e;
			TransportId id = t.getTransportId();
			State state = t.getState();
			if (LOG.isLoggable(INFO)) {
				LOG.info("TransportStateEvent: " + id + " is " + state);
			}
			MutableLiveData<State> liveData = getPluginLiveData(id);
			if (liveData != null) liveData.postValue(state);
		}
	}

	LiveData<State> getPluginState(TransportId id) {
		LiveData<State> liveData = getPluginLiveData(id);
		if (liveData == null) throw new IllegalArgumentException();
		return liveData;
	}

	LiveData<Boolean> getPluginEnabledSetting(TransportId id) {
		if (id.equals(TorConstants.ID)) return torEnabledSetting;
		else if (id.equals(LanTcpConstants.ID)) return wifiEnabledSetting;
		else if (id.equals(BluetoothConstants.ID)) return btEnabledSetting;
		else throw new IllegalArgumentException();
	}

	LiveData<NetworkStatus> getNetworkStatus() {
		return networkStatus;
	}

	LiveData<Boolean> getBluetoothTurnedOn() {
		return bluetoothTurnedOn;
	}

	void enableTransport(TransportId id, boolean enable) {
		Settings s = new Settings();
		s.putBoolean(PREF_PLUGIN_ENABLE, enable);
		mergeSettings(s, id.getString());
	}

	private void loadSettings() {
		dbExecutor.execute(() -> {
			try {
				boolean tor = isPluginEnabled(TorConstants.ID, true);
				torEnabledSetting.postValue(tor);
				boolean wifi = isPluginEnabled(LanTcpConstants.ID, false);
				wifiEnabledSetting.postValue(wifi);
				boolean bt = isPluginEnabled(BluetoothConstants.ID, false);
				btEnabledSetting.postValue(bt);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	private boolean isPluginEnabled(TransportId id, boolean defaultValue)
			throws DbException {
		Settings s = settingsManager.getSettings(id.getString());
		return s.getBoolean(PREF_PLUGIN_ENABLE, defaultValue);
	}

	private State getTransportState(TransportId id) {
		Plugin plugin = pluginManager.getPlugin(id);
		return plugin == null ? STARTING_STOPPING : plugin.getState();
	}

	@Nullable
	private MutableLiveData<State> getPluginLiveData(TransportId id) {
		if (id.equals(TorConstants.ID)) return torPluginState;
		else if (id.equals(LanTcpConstants.ID)) return wifiPluginState;
		else if (id.equals(BluetoothConstants.ID)) return btPluginState;
		else return null;
	}

	private void mergeSettings(Settings s, String namespace) {
		dbExecutor.execute(() -> {
			try {
				long start = now();
				settingsManager.mergeSettings(s, namespace);
				logDuration(LOG, "Merging settings", start);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	private class BluetoothStateReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			int state = intent.getIntExtra(EXTRA_STATE, 0);
			if (state == STATE_ON) bluetoothTurnedOn.postValue(true);
			else bluetoothTurnedOn.postValue(false);
		}
	}
}
