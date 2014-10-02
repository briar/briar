package org.briarproject.plugins.modem;

import java.util.concurrent.Executor;

import org.briarproject.api.TransportId;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;
import org.briarproject.api.reliability.ReliabilityLayerFactory;
import org.briarproject.util.StringUtils;

public class ModemPluginFactory implements DuplexPluginFactory {

	private static final int MAX_FRAME_LENGTH = 1024;
	private static final long MAX_LATENCY = 60 * 1000; // 1 minute
	private static final long POLLING_INTERVAL = 60 * 60 * 1000; // 1 hour

	private final Executor ioExecutor;
	private final ModemFactory modemFactory;
	private final SerialPortList serialPortList;

	public ModemPluginFactory(Executor ioExecutor,
			ReliabilityLayerFactory reliabilityFactory) {
		this.ioExecutor = ioExecutor;
		modemFactory = new ModemFactoryImpl(ioExecutor, reliabilityFactory);
		serialPortList = new SerialPortListImpl();
	}

	public TransportId getId() {
		return ModemPlugin.ID;
	}

	public DuplexPlugin createPlugin(DuplexPluginCallback callback) {
		// This plugin is not enabled by default
		String enabled = callback.getConfig().get("enabled");
		if(StringUtils.isNullOrEmpty(enabled)) return null;
		return new ModemPlugin(ioExecutor, modemFactory, serialPortList,
				callback, MAX_FRAME_LENGTH, MAX_LATENCY, POLLING_INTERVAL,
				true);
	}
}
