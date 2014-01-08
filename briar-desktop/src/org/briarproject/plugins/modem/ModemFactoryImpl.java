package org.briarproject.plugins.modem;

import java.util.concurrent.Executor;

import org.briarproject.api.reliability.ReliabilityLayerFactory;
import org.briarproject.api.system.Clock;
import org.briarproject.api.system.SystemClock;

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
