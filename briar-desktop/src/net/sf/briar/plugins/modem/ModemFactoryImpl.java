package net.sf.briar.plugins.modem;

import java.util.concurrent.Executor;

import net.sf.briar.api.reliability.ReliabilityLayerFactory;
import net.sf.briar.api.system.Clock;
import net.sf.briar.api.system.SystemClock;

class ModemFactoryImpl implements ModemFactory {

	private final Executor executor;
	private final ReliabilityLayerFactory reliabilityFactory;
	private final Clock clock;

	ModemFactoryImpl(Executor executor,
			ReliabilityLayerFactory reliabilityFactory) {
		this.executor = executor;
		this.reliabilityFactory = reliabilityFactory;
		clock = new SystemClock();
	}

	public Modem createModem(Modem.Callback callback, String portName) {
		return new ModemImpl(executor, reliabilityFactory, clock, callback,
				new SerialPortImpl(portName));
	}
}
