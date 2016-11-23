package org.briarproject.bramble.plugin.modem;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
interface ModemFactory {

	Modem createModem(Modem.Callback callback, String portName);
}
