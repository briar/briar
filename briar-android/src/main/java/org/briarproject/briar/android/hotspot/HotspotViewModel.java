package org.briarproject.briar.android.hotspot;

import android.app.Application;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
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

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static java.util.Objects.requireNonNull;
import static java.util.logging.Logger.getLogger;

@NotNullByDefault
class HotspotViewModel extends DbViewModel
		implements HotspotListener, WebServerListener {

	private static final Logger LOG =
			getLogger(HotspotViewModel.class.getName());

	@IoExecutor
	private final Executor ioExecutor;
	private final AndroidNotificationManager notificationManager;
	private final HotspotManager hotspotManager;
	private final WebServerManager webServerManager;

	private final MutableLiveData<HotspotState> state =
			new MutableLiveData<>();
	private final MutableLiveEvent<Boolean> peerConnected =
			new MutableLiveEvent<>();

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
			HotspotManager hotspotManager,
			WebServerManager webServerManager,
			AndroidNotificationManager notificationManager) {
		super(app, dbExecutor, lifecycleManager, db, androidExecutor);
		this.ioExecutor = ioExecutor;
		this.notificationManager = notificationManager;
		this.hotspotManager = hotspotManager;
		this.hotspotManager.setHotspotListener(this);
		this.webServerManager = webServerManager;
		this.webServerManager.setListener(this);
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
		NetworkConfig nc = requireNonNull(networkConfig);
		state.postValue(new HotspotStarted(nc, websiteConfig));
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
