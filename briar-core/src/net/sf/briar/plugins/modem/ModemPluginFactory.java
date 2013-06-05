package net.sf.briar.plugins.modem;

import java.util.concurrent.Executor;

import net.sf.briar.api.TransportId;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexPluginFactory;
import net.sf.briar.api.reliability.ReliabilityLayerFactory;
import net.sf.briar.util.StringUtils;

public class ModemPluginFactory implements DuplexPluginFactory {

	private static final int MAX_FRAME_LENGTH = 1024;
	private static final long MAX_LATENCY = 60 * 1000; // 1 minute
	private static final long POLLING_INTERVAL = 60 * 60 * 1000; // 1 hour

	private final Executor pluginExecutor;
	private final ModemFactory modemFactory;
	private final SerialPortList serialPortList;

	public ModemPluginFactory(Executor pluginExecutor,
			ReliabilityLayerFactory reliabilityFactory) {
		this.pluginExecutor = pluginExecutor;
		modemFactory = new ModemFactoryImpl(pluginExecutor, reliabilityFactory);
		serialPortList = new SerialPortListImpl();
	}

	public TransportId getId() {
		return ModemPlugin.ID;
	}

	public DuplexPlugin createPlugin(DuplexPluginCallback callback) {
		// This plugin is not enabled by default
		String enabled = callback.getConfig().get("enabled");
		if(StringUtils.isNullOrEmpty(enabled)) return null;
		return new ModemPlugin(pluginExecutor, modemFactory, serialPortList,
				callback, MAX_FRAME_LENGTH, MAX_LATENCY, POLLING_INTERVAL,
				true);
	}
}
