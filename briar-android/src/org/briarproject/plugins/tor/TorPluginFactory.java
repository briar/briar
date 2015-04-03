package org.briarproject.plugins.tor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import org.briarproject.api.TransportId;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;
import org.briarproject.api.system.LocationUtils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;

public class TorPluginFactory implements DuplexPluginFactory {

	private static final Logger LOG =
			Logger.getLogger(TorPluginFactory.class.getName());

	private static final int MAX_LATENCY = 30 * 1000; // 30 seconds
	private static final int MAX_IDLE_TIME = 30 * 1000; // 30 seconds
	private static final int POLLING_INTERVAL = 3 * 60 * 1000; // 3 minutes

	private final Executor ioExecutor;
	private final Context appContext;
	private final LocationUtils locationUtils;

	public TorPluginFactory(Executor ioExecutor, Context appContext,
			LocationUtils locationUtils) {
		this.ioExecutor = ioExecutor;
		this.appContext = appContext;
		this.locationUtils = locationUtils;
	}

	public TransportId getId() {
		return TorPlugin.ID;
	}

	@SuppressLint("NewApi")
	@SuppressWarnings("deprecation")
	public DuplexPlugin createPlugin(DuplexPluginCallback callback) {
		// Check that we have a Tor binary for this architecture
		List<String> abis = new ArrayList<String>();
		if(Build.VERSION.SDK_INT >= 21) {
			for(String abi : Build.SUPPORTED_ABIS) abis.add(abi);
		} else {
			abis.add(Build.CPU_ABI);
			if(Build.CPU_ABI2 != null) abis.add(Build.CPU_ABI2);
		}
		boolean supported = false;
		for(String abi : abis) if(abi.startsWith("armeabi")) supported = true;
		if(!supported) {
			LOG.info("Tor is not supported on this architecture");
			return null;
		}
		return new TorPlugin(ioExecutor,appContext, locationUtils, callback,
				MAX_LATENCY, MAX_IDLE_TIME, POLLING_INTERVAL);
	}
}
