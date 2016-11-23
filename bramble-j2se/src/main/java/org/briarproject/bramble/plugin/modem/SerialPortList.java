package org.briarproject.bramble.plugin.modem;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
interface SerialPortList {

	String[] getPortNames();
}
