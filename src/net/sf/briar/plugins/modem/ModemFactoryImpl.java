package net.sf.briar.plugins.modem;

import java.util.concurrent.Executor;

class ModemFactoryImpl implements ModemFactory {

	private final Executor executor;

	ModemFactoryImpl(Executor executor) {
		this.executor = executor;
	}

	public Modem createModem(Modem.Callback callback, String portName) {
		return new ModemImpl(executor, callback, portName);
	}
}
