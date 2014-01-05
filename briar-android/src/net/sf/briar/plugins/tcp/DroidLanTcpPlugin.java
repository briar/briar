package net.sf.briar.plugins.tcp;

import static android.content.Context.WIFI_SERVICE;

import java.util.concurrent.Executor;

import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
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
