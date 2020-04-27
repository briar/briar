package org.briarproject.briar.android.navdrawer;

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

import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.Plugin.State.STARTING_STOPPING;

@NotNullByDefault
public class PluginViewModel extends ViewModel implements EventListener {

	private static final Logger LOG =
			getLogger(PluginViewModel.class.getName());

	private final PluginManager pluginManager;
	private final EventBus eventBus;

	private final MutableLiveData<State> torPluginState =
			new MutableLiveData<>();
	private final MutableLiveData<State> wifiPluginState =
			new MutableLiveData<>();
	private final MutableLiveData<State> btPluginState =
			new MutableLiveData<>();

	@Inject
	PluginViewModel(PluginManager pluginManager, EventBus eventBus) {
		this.pluginManager = pluginManager;
		this.eventBus = eventBus;
		eventBus.addListener(this);
		torPluginState.setValue(getTransportState(TorConstants.ID));
		wifiPluginState.setValue(getTransportState(LanTcpConstants.ID));
		btPluginState.setValue(getTransportState(BluetoothConstants.ID));
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
			if (liveData != null) liveData.postValue(state);
		}
	}

	LiveData<State> getPluginState(TransportId t) {
		LiveData<State> liveData = getPluginLiveData(t);
		if (liveData == null) throw new IllegalArgumentException();
		return liveData;
	}

	private State getTransportState(TransportId id) {
		Plugin plugin = pluginManager.getPlugin(id);
		return plugin == null ? STARTING_STOPPING : plugin.getState();
	}

	@Nullable
	private MutableLiveData<State> getPluginLiveData(TransportId t) {
		if (t.equals(TorConstants.ID)) return torPluginState;
		else if (t.equals(LanTcpConstants.ID)) return wifiPluginState;
		else if (t.equals(BluetoothConstants.ID)) return btPluginState;
		else return null;
	}
}
