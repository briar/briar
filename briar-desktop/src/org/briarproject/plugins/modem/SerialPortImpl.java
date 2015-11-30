package org.briarproject.plugins.modem;

import java.io.IOException;

import jssc.SerialPortEventListener;
import jssc.SerialPortException;

class SerialPortImpl implements SerialPort {

	private final jssc.SerialPort port;

	SerialPortImpl(String portName) {
		port = new jssc.SerialPort(portName);
	}

	public void openPort() throws IOException {
		try {
			if (!port.openPort()) throw new IOException("Failed to open port");
		} catch (SerialPortException e) {
			throw new IOException(e);
		}
	}

	public void closePort() throws IOException {
		try {
			if (!port.closePort()) throw new IOException("Failed to close port");
		} catch (SerialPortException e) {
			throw new IOException(e);
		}
	}

	public boolean setParams(int baudRate, int dataBits, int stopBits,
			int parityBits) throws IOException {
		try {
			return port.setParams(baudRate, dataBits, stopBits, parityBits);
		} catch (SerialPortException e) {
			throw new IOException(e);
		}
	}

	public void purgePort(int flags) throws IOException {
		try {
			if (!port.purgePort(flags))
				throw new IOException("Failed to purge port");
		} catch (SerialPortException e) {
			throw new IOException(e);
		}
	}

	public void addEventListener(SerialPortEventListener l) throws IOException {
		try {
			port.addEventListener(l);
		} catch (SerialPortException e) {
			throw new IOException(e);
		}
	}

	public byte[] readBytes() throws IOException {
		try {
			return port.readBytes();
		} catch (SerialPortException e) {
			throw new IOException(e);
		}
	}

	public void writeBytes(byte[] b) throws IOException {
		try {
			if (!port.writeBytes(b)) throw new IOException("Failed to write");
		} catch (SerialPortException e) {
			throw new IOException(e);
		}
	}
}
