package org.briarproject.plugins.tcp;

import static android.content.Context.WIFI_SERVICE;

import java.util.concurrent.Executor;

import org.briarproject.api.crypto.PseudoRandom;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.system.Clock;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;

class DroidLanTcpPlugin extends LanTcpPlugin {

	private final Context appContext;

	DroidLanTcpPlugin(Executor pluginExecutor, Context appContext, Clock clock,
			DuplexPluginCallback callback, int maxFrameLength, long maxLatency,
			long pollingInterval) {
		super(pluginExecutor, clock, callback, maxFrameLength, maxLatency,
				pollingInterval);
		this.appContext = appContext;
	}

	@Override
	public DuplexTransportConnection createInvitationConnection(PseudoRandom r,
			long timeout) {
		Object o = appContext.getSystemService(WIFI_SERVICE);
		WifiManager wifi = (WifiManager) o;
		if(wifi == null || !wifi.isWifiEnabled()) return null;
		MulticastLock lock = wifi.createMulticastLock("invitation");
		lock.acquire();
		try {
			return super.createInvitationConnection(r, timeout);
		} finally {
			lock.release();
		}
	}
}
