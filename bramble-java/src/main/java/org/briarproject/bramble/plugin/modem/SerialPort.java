package org.briarproject.bramble.plugin.modem;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.IOException;

import jssc.SerialPortEventListener;

@NotNullByDefault
interface SerialPort {

	void openPort() throws IOException;

	void closePort() throws IOException;

	boolean setParams(int baudRate, int dataBits, int stopBits, int parityBits)
			throws IOException;

	void purgePort(int flags) throws IOException;

	void addEventListener(SerialPortEventListener l) throws IOException;

	byte[] readBytes() throws IOException;

	void writeBytes(byte[] b) throws IOException;
}
