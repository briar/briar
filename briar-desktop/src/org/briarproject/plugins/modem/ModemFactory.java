package org.briarproject.plugins.modem;

interface ModemFactory {

	Modem createModem(Modem.Callback callback, String portName);
}
