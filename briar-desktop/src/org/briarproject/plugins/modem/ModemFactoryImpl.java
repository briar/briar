package org.briarproject.plugins.modem;

import java.util.concurrent.Executor;

import org.briarproject.api.reliability.ReliabilityLayerFactory;
import org.briarproject.api.system.Clock;
import org.briarproject.system.SystemClock;

class ModemFactoryImpl implements ModemFactory {

	private final Executor ioExecutor;
	private final ReliabilityLayerFactory reliabilityFactory;
	private final Clock clock;

	ModemFactoryImpl(Executor ioExecutor,
			ReliabilityLayerFactory reliabilityFactory) {
		this.ioExecutor = ioExecutor;
		this.reliabilityFactory = reliabilityFactory;
		clock = new SystemClock();
	}

	public Modem createModem(Modem.Callback callback, String portName) {
		return new ModemImpl(ioExecutor, reliabilityFactory, clock, callback,
				new SerialPortImpl(portName));
	}
}
