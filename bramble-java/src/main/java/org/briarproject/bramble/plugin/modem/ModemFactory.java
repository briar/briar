package org.briarproject.bramble.plugin.modem;

import org.briarproject.nullsafety.NotNullByDefault;

@NotNullByDefault
interface ModemFactory {

	Modem createModem(Modem.Callback callback, String portName);
}
