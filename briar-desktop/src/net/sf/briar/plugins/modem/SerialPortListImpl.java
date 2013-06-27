package net.sf.briar.plugins.modem;

class SerialPortListImpl implements SerialPortList {

	public String[] getPortNames() {
		return jssc.SerialPortList.getPortNames();
	}
}
