package net.sf.briar.plugins.modem;

interface ModemFactory {

	Modem createModem(Modem.Callback callback, String portName);
}
