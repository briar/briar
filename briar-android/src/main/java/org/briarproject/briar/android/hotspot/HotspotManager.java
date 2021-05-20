package org.briarproject.briar.android.hotspot;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.GroupInfoListener;
import android.os.Handler;
import android.util.DisplayMetrics;

import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.briar.R;
import org.briarproject.briar.android.hotspot.HotspotState.NetworkConfig;
import org.briarproject.briar.android.util.QrCodeUtils;

import java.security.SecureRandom;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.UiThread;

import static android.content.Context.WIFI_P2P_SERVICE;
import static android.content.Context.WIFI_SERVICE;
import static android.net.wifi.WifiManager.WIFI_MODE_FULL;
import static android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF;
import static android.net.wifi.p2p.WifiP2pConfig.GROUP_OWNER_BAND_2GHZ;
import static android.net.wifi.p2p.WifiP2pManager.BUSY;
import static android.net.wifi.p2p.WifiP2pManager.ERROR;
import static android.net.wifi.p2p.WifiP2pManager.NO_SERVICE_REQUESTS;
import static android.net.wifi.p2p.WifiP2pManager.P2P_UNSUPPORTED;
import static android.os.Build.VERSION.SDK_INT;
import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;

class HotspotManager implements ActionListener {

	interface HotspotListener {
		void onStartingHotspot();

		@IoExecutor
		void onHotspotStarted(NetworkConfig networkConfig);

		@UiThread
		void onDeviceConnected();

		void onHotspotStopped();

		void onHotspotError(String error);
	}

	private static final Logger LOG = getLogger(HotspotManager.class.getName());

	private static final int MAX_GROUP_INFO_ATTEMPTS = 5;
	private static final int RETRY_DELAY_MILLIS = 1000;

	private final Context ctx;
	@IoExecutor
	private final Executor ioExecutor;
	private final SecureRandom random;
	private final HotspotListener listener;
	private final WifiManager wifiManager;
	private final WifiP2pManager wifiP2pManager;
	private final Handler handler;
	private final String lockTag;

	@Nullable
	// on API < 29 this is null because we cannot request a custom network name
	private String networkName = null;

	private WifiManager.WifiLock wifiLock;
	private WifiP2pManager.Channel channel;

	HotspotManager(Context ctx, @IoExecutor Executor ioExecutor,
			SecureRandom random, HotspotListener listener) {
		this.ctx = ctx;
		this.ioExecutor = ioExecutor;
		this.random = random;
		this.listener = listener;
		wifiManager = (WifiManager) ctx.getApplicationContext()
				.getSystemService(WIFI_SERVICE);
		wifiP2pManager =
				(WifiP2pManager) ctx.getSystemService(WIFI_P2P_SERVICE);
		handler = new Handler(ctx.getMainLooper());
		lockTag = ctx.getPackageName() + ":app-sharing-hotspot";
	}

	@UiThread
	void startWifiP2pHotspot() {
		if (wifiP2pManager == null) {
			listener.onHotspotError(
					ctx.getString(R.string.hotspot_error_no_wifi_direct));
			return;
		}
		listener.onStartingHotspot();
		channel = wifiP2pManager.initialize(ctx, ctx.getMainLooper(), null);
		if (channel == null) {
			listener.onHotspotError(
					ctx.getString(R.string.hotspot_error_no_wifi_direct));
			return;
		}
		acquireLock();
		try {
			if (SDK_INT >= 29) {
				networkName = getNetworkName();
				String passphrase = getPassphrase();
				WifiP2pConfig config = new WifiP2pConfig.Builder()
						.setGroupOperatingBand(GROUP_OWNER_BAND_2GHZ)
						.setNetworkName(networkName)
						.setPassphrase(passphrase)
						.build();
				wifiP2pManager.createGroup(channel, config, this);
			} else {
				wifiP2pManager.createGroup(channel, this);
			}
		} catch (SecurityException e) {
			// this should never happen, because we request permissions before
			throw new AssertionError(e);
		}
	}

	@Override
	// Callback for wifiP2pManager#createGroup() during startWifiP2pHotspot()
	public void onSuccess() {
		requestGroupInfo(1);
	}

	@Override
	// Callback for wifiP2pManager#createGroup() during startWifiP2pHotspot()
	public void onFailure(int reason) {
		if (reason == BUSY) {
			// Hotspot already running
			requestGroupInfo(1);
		} else if (reason == P2P_UNSUPPORTED) {
			releaseHotspotWithError(ctx.getString(
					R.string.hotspot_error_start_callback_failed,
					"p2p unsupported"));
		} else if (reason == ERROR) {
			releaseHotspotWithError(ctx.getString(
					R.string.hotspot_error_start_callback_failed, "p2p error"));
		} else if (reason == NO_SERVICE_REQUESTS) {
			releaseHotspotWithError(ctx.getString(
					R.string.hotspot_error_start_callback_failed,
					"no service requests"));
		} else {
			// all cases covered, in doubt set to error
			releaseHotspotWithError(ctx.getString(
					R.string.hotspot_error_start_callback_failed_unknown,
					reason));
		}
	}

	@RequiresApi(29)
	private String getNetworkName() {
		return "DIRECT-" + getRandomString(2) + "-" +
				getRandomString(10);
	}

	private String getPassphrase() {
		return getRandomString(8);
	}

	void stopWifiP2pHotspot() {
		if (channel == null) return;
		wifiP2pManager.removeGroup(channel, new ActionListener() {
			@Override
			public void onSuccess() {
				releaseHotspot();
			}

			@Override
			public void onFailure(int reason) {
				// not propagating back error
				releaseHotspot();
			}
		});
	}

	private void acquireLock() {
		// WIFI_MODE_FULL has no effect on API >= 29
		int lockType =
				SDK_INT >= 29 ? WIFI_MODE_FULL_HIGH_PERF : WIFI_MODE_FULL;
		wifiLock = wifiManager.createWifiLock(lockType, lockTag);
		wifiLock.acquire();
	}

	private void releaseHotspot() {
		listener.onHotspotStopped();
		closeChannelAndReleaseLock();
	}

	private void releaseHotspotWithError(String error) {
		listener.onHotspotError(error);
		closeChannelAndReleaseLock();
	}

	private void closeChannelAndReleaseLock() {
		if (SDK_INT >= 27) channel.close();
		channel = null;
		wifiLock.release();
	}

	private void requestGroupInfo(int attempt) {
		if (LOG.isLoggable(INFO)) {
			LOG.info("requestGroupInfo attempt: " + attempt);
		}
		GroupInfoListener groupListener = group -> {
			boolean valid = isGroupValid(group);
			// If the group is valid, set the hotspot to started. If we don't
			// have any attempts left, we try what we got
			if (valid || attempt >= MAX_GROUP_INFO_ATTEMPTS) {
				onHotspotStarted(group);
			} else {
				retryRequestingGroupInfo(attempt + 1);
			}
		};
		try {
			if (channel == null) return;
			wifiP2pManager.requestGroupInfo(channel, groupListener);
		} catch (SecurityException e) {
			// this should never happen, because we request permissions before
			throw new AssertionError(e);
		}
	}

	@UiThread
	private void onHotspotStarted(WifiP2pGroup group) {
		DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
		ioExecutor.execute(() -> {
			String content = createWifiLoginString(group.getNetworkName(),
					group.getPassphrase());
			Bitmap qrCode = QrCodeUtils.createQrCode(dm, content);
			NetworkConfig config = new NetworkConfig(group.getNetworkName(),
					group.getPassphrase(), qrCode);
			listener.onHotspotStarted(config);
		});
		requestGroupInfoForConnection();
	}

	private boolean isGroupValid(@Nullable WifiP2pGroup group) {
		if (group == null) {
			LOG.info("group is null");
			return false;
		} else if (!group.getNetworkName().startsWith("DIRECT-")) {
			if (LOG.isLoggable(INFO)) {
				LOG.info("received networkName without prefix 'DIRECT-': " +
						group.getNetworkName());
			}
			return false;
		} else if (networkName != null &&
				!networkName.equals(group.getNetworkName())) {
			if (LOG.isLoggable(INFO)) {
				LOG.info("expected networkName: " + networkName);
				LOG.info("received networkName: " + group.getNetworkName());
			}
			return false;
		}
		return true;
	}

	private void retryRequestingGroupInfo(int attempt) {
		LOG.info("retrying");
		// On some devices we need to wait for the group info to become available
		if (attempt < MAX_GROUP_INFO_ATTEMPTS) {
			handler.postDelayed(() -> requestGroupInfo(attempt + 1),
					RETRY_DELAY_MILLIS);
		} else {
			releaseHotspotWithError(ctx.getString(
					R.string.hotspot_error_start_callback_no_group_info));
		}
	}

	@UiThread
	private void requestGroupInfoForConnection() {
		if (LOG.isLoggable(INFO)) {
			LOG.info("requestGroupInfo for connection");
		}
		GroupInfoListener groupListener = group -> {
			if (group == null || group.getClientList().isEmpty()) {
				handler.postDelayed(this::requestGroupInfoForConnection,
						RETRY_DELAY_MILLIS);
			} else {
				if (LOG.isLoggable(INFO)) {
					LOG.info("client list " + group.getClientList());
				}
				listener.onDeviceConnected();
			}
		};
		try {
			if (channel == null) return;
			wifiP2pManager.requestGroupInfo(channel, groupListener);
		} catch (SecurityException e) {
			throw new AssertionError(e);
		}
	}

	private static String createWifiLoginString(String ssid, String password) {
		// https://en.wikipedia.org/wiki/QR_code#WiFi_network_login
		// do not remove the dangling ';', it can cause problems to omit it
		return "WIFI:S:" + ssid + ";T:WPA;P:" + password + ";;";
	}

	private static final String digits = "123456789"; // avoid 0
	private static final String letters = "abcdefghijkmnopqrstuvwxyz"; // no l
	private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // no I, O

	private String getRandomString(int length) {
		char[] c = new char[length];
		for (int i = 0; i < length; i++) {
			if (random.nextBoolean()) {
				c[i] = random(digits);
			} else if (random.nextBoolean()) {
				c[i] = random(letters);
			} else {
				c[i] = random(LETTERS);
			}
		}
		return new String(c);
	}

	private char random(String universe) {
		return universe.charAt(random.nextInt(universe.length()));
	}

}
