package org.briarproject.bramble.plugin.modem;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
class SerialPortListImpl implements SerialPortList {

	@Override
	public String[] getPortNames() {
		return jssc.SerialPortList.getPortNames();
	}
}
