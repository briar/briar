package org.briarproject.briar.android.hotspot;

import android.app.Application;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.R;
import org.briarproject.briar.android.hotspot.HotspotManager.HotspotListener;
import org.briarproject.briar.android.hotspot.HotspotState.HotspotError;
import org.briarproject.briar.android.hotspot.HotspotState.HotspotStarted;
import org.briarproject.briar.android.hotspot.HotspotState.NetworkConfig;
import org.briarproject.briar.android.hotspot.HotspotState.StartingHotspot;
import org.briarproject.briar.android.hotspot.HotspotState.WebsiteConfig;
import org.briarproject.briar.android.hotspot.WebServerManager.WebServerListener;
import org.briarproject.briar.android.viewmodel.DbViewModel;
import org.briarproject.briar.android.viewmodel.LiveEvent;
import org.briarproject.briar.android.viewmodel.MutableLiveEvent;
import org.briarproject.briar.api.android.AndroidNotificationManager;

import java.security.SecureRandom;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.UiThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static android.os.Build.VERSION.SDK_INT;
import static java.util.logging.Logger.getLogger;

@NotNullByDefault
class HotspotViewModel extends DbViewModel
		implements HotspotListener, WebServerListener {

	private static final Logger LOG =
			getLogger(HotspotViewModel.class.getName());
	private static final String HOTSPOT_NAMESPACE = "hotspot";
	private static final String HOTSPOT_KEY_SSID = "ssid";
	private static final String HOTSPOT_KEY_PASS = "pass";

	@IoExecutor
	private final Executor ioExecutor;
	private final SettingsManager settingsManager;
	private final SecureRandom random;
	private final AndroidNotificationManager notificationManager;
	private final HotspotManager hotspotManager;
	private final WebServerManager webServerManager;

	private final MutableLiveData<HotspotState> state =
			new MutableLiveData<>();
	private final MutableLiveEvent<Boolean> peerConnected =
			new MutableLiveEvent<>();
	private final MutableLiveData<NetworkConfig> savedNetworkConfig =
			new MutableLiveData<>();

	@Nullable
	// Field to temporarily store the network config received via onHotspotStarted()
	// in order to post it along with a HotspotStarted status
	private volatile NetworkConfig networkConfig;

	@Inject
	HotspotViewModel(Application app,
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			TransactionManager db,
			AndroidExecutor androidExecutor,
			@IoExecutor Executor ioExecutor,
			SettingsManager settingsManager,
			SecureRandom secureRandom,
			AndroidNotificationManager notificationManager) {
		super(app, dbExecutor, lifecycleManager, db, androidExecutor);
		this.ioExecutor = ioExecutor;
		this.settingsManager = settingsManager;
		this.random = secureRandom;
		this.notificationManager = notificationManager;
		hotspotManager =
				new HotspotManager(app, ioExecutor, savedNetworkConfig, this);
		webServerManager = new WebServerManager(app, this);
		// get or set persistent SSID and password
		if (SDK_INT >= 29) getOrSetNetworkConfig();
	}

	/**
	 * Store persistent Wi-Fi SSID and passphrase in Settings to improve UX
	 * so that users don't have to change them when attempting to connect.
	 * Works only on API 29 and above.
	 */
	@RequiresApi(29)
	private void getOrSetNetworkConfig() {
		runOnDbThread(false, txn -> {
			Settings settings =
					settingsManager.getSettings(txn, HOTSPOT_NAMESPACE);
			String ssid = settings.get(HOTSPOT_KEY_SSID);
			String pass = settings.get(HOTSPOT_KEY_PASS);
			if (ssid == null || pass == null) {
				ssid = HotspotManager.getSsid(random);
				pass = HotspotManager.getPassword(random);
				settings.put(HOTSPOT_KEY_SSID, ssid);
				settings.put(HOTSPOT_KEY_PASS, pass);
				settingsManager.mergeSettings(txn, settings, HOTSPOT_NAMESPACE);
			}
			savedNetworkConfig.postValue(new NetworkConfig(ssid, pass, null));
		}, error -> {
			handleException(error);
			// probably never happens, but if lets use non-persistent data
			String ssid = HotspotManager.getSsid(random);
			String pass = HotspotManager.getPassword(random);
			savedNetworkConfig.postValue(new NetworkConfig(ssid, pass, null));
		});
	}

	@UiThread
	void startHotspot() {
		HotspotState s = state.getValue();
		if (s instanceof HotspotStarted) {
			// Don't try to start again, if already started, just re-set value.
			// This can happen if the user navigates back to intro fragment.
			state.setValue(s);
		} else {
			hotspotManager.startWifiP2pHotspot();
			notificationManager.showHotspotNotification();
		}
	}

	@UiThread
	private void stopHotspot() {
		ioExecutor.execute(webServerManager::stopWebServer);
		hotspotManager.stopWifiP2pHotspot();
		notificationManager.clearHotspotNotification();
	}

	@Override
	protected void onCleared() {
		super.onCleared();
		stopHotspot();
	}

	@Override
	public void onStartingHotspot() {
		state.setValue(new StartingHotspot());
	}

	@Override
	@IoExecutor
	public void onHotspotStarted(NetworkConfig networkConfig) {
		this.networkConfig = networkConfig;
		LOG.info("starting webserver");
		webServerManager.startWebServer();
	}

	@UiThread
	@Override
	public void onDeviceConnected() {
		peerConnected.setEvent(true);
	}

	@Override
	public void onHotspotStopped() {
		LOG.info("stopping webserver");
		ioExecutor.execute(webServerManager::stopWebServer);
	}

	@Override
	public void onHotspotError(String error) {
		state.setValue(new HotspotError(error));
		ioExecutor.execute(webServerManager::stopWebServer);
		notificationManager.clearHotspotNotification();
	}

	@Override
	@IoExecutor
	public void onWebServerStarted(WebsiteConfig websiteConfig) {
		state.postValue(new HotspotStarted(networkConfig, websiteConfig));
		networkConfig = null;
	}

	@Override
	@IoExecutor
	public void onWebServerError() {
		state.postValue(new HotspotError(getApplication()
				.getString(R.string.hotspot_error_web_server_start)));
		hotspotManager.stopWifiP2pHotspot();
	}

	LiveData<HotspotState> getState() {
		return state;
	}

	LiveEvent<Boolean> getPeerConnectedEvent() {
		return peerConnected;
	}

}
