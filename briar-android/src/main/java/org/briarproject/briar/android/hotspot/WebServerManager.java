package org.briarproject.briar.android.hotspot;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.DisplayMetrics;

import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.briar.android.hotspot.HotspotState.WebsiteConfig;
import org.briarproject.briar.android.util.QrCodeUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Logger;

import androidx.annotation.Nullable;

import static java.util.Collections.emptyList;
import static java.util.Collections.list;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.briar.android.hotspot.WebServer.PORT;

class WebServerManager {

	interface WebServerListener {
		@IoExecutor
		void onWebServerStarted(WebsiteConfig websiteConfig);

		@IoExecutor
		void onWebServerError();
	}

	private static final Logger LOG =
			getLogger(WebServerManager.class.getName());

	private final WebServer webServer;
	private final WebServerListener listener;
	private final DisplayMetrics dm;

	WebServerManager(Context ctx, WebServerListener listener) {
		this.listener = listener;
		webServer = new WebServer(ctx);
		dm = ctx.getResources().getDisplayMetrics();
	}

	@IoExecutor
	void startWebServer() {
		try {
			webServer.start();
			onWebServerStarted();
		} catch (IOException e) {
			logException(LOG, WARNING, e);
			listener.onWebServerError();
		}
	}

	@IoExecutor
	private void onWebServerStarted() {
		String url = "http://192.168.49.1:" + PORT;
		InetAddress address = getAccessPointAddress();
		if (address == null) {
			LOG.info(
					"Could not find access point address, assuming 192.168.49.1");
		} else {
			if (LOG.isLoggable(INFO)) {
				LOG.info("Access point address " + address.getHostAddress());
			}
			url = "http://" + address.getHostAddress() + ":" + PORT;
		}
		Bitmap qrCode = QrCodeUtils.createQrCode(dm, url);
		listener.onWebServerStarted(new WebsiteConfig(url, qrCode));
	}

	/**
	 * It is safe to call this more than once and it won't throw.
	 */
	@IoExecutor
	void stopWebServer() {
		webServer.stop();
	}

	@Nullable
	private static InetAddress getAccessPointAddress() {
		for (NetworkInterface i : getNetworkInterfaces()) {
			if (i.getName().startsWith("p2p")) {
				for (InterfaceAddress a : i.getInterfaceAddresses()) {
					// we consider only IPv4 addresses
					if (a.getAddress().getAddress().length == 4)
						return a.getAddress();
				}
			}
		}
		return null;
	}

	private static List<NetworkInterface> getNetworkInterfaces() {
		try {
			Enumeration<NetworkInterface> ifaces =
					NetworkInterface.getNetworkInterfaces();
			return ifaces == null ? emptyList() : list(ifaces);
		} catch (SocketException e) {
			logException(LOG, WARNING, e);
			return emptyList();
		}
	}

}
