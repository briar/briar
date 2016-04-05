package org.briarproject.plugins.modem;

import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.PseudoRandom;
import org.briarproject.api.keyagreement.KeyAgreementListener;
import org.briarproject.api.keyagreement.TransportDescriptor;
import org.briarproject.api.plugins.duplex.AbstractDuplexTransportConnection;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.properties.TransportProperties;
import org.briarproject.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

class ModemPlugin implements DuplexPlugin, Modem.Callback {

	static final TransportId ID = new TransportId("modem");

	private static final Logger LOG =
			Logger.getLogger(ModemPlugin.class.getName());

	private final ModemFactory modemFactory;
	private final SerialPortList serialPortList;
	private final DuplexPluginCallback callback;
	private final int maxLatency;

	private volatile boolean running = false;
	private volatile Modem modem = null;

	ModemPlugin(ModemFactory modemFactory, SerialPortList serialPortList,
			DuplexPluginCallback callback, int maxLatency) {
		this.modemFactory = modemFactory;
		this.serialPortList = serialPortList;
		this.callback = callback;
		this.maxLatency = maxLatency;
	}

	public TransportId getId() {
		return ID;
	}

	public int getMaxLatency() {
		return maxLatency;
	}

	public int getMaxIdleTime() {
		// FIXME: Do we need keepalives for this transport?
		return Integer.MAX_VALUE;
	}

	public boolean start() {
		for (String portName : serialPortList.getPortNames()) {
			if (LOG.isLoggable(INFO))
				LOG.info("Trying to initialise modem on " + portName);
			modem = modemFactory.createModem(this, portName);
			try {
				if (!modem.start()) continue;
				if (LOG.isLoggable(INFO))
					LOG.info("Initialised modem on " + portName);
				running = true;
				return true;
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
		return false;
	}

	public void stop() {
		running = false;
		if (modem != null) {
			try {
				modem.stop();
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	public boolean isRunning() {
		return running;
	}

	public boolean shouldPoll() {
		return false;
	}

	public int getPollingInterval() {
		throw new UnsupportedOperationException();
	}

	public void poll(Collection<ContactId> connected) {
		throw new UnsupportedOperationException();
	}

	boolean resetModem() {
		if (!running) return false;
		for (String portName : serialPortList.getPortNames()) {
			if (LOG.isLoggable(INFO))
				LOG.info("Trying to initialise modem on " + portName);
			modem = modemFactory.createModem(this, portName);
			try {
				if (!modem.start()) continue;
				if (LOG.isLoggable(INFO))
					LOG.info("Initialised modem on " + portName);
				return true;
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
		running = false;
		return false;
	}

	public DuplexTransportConnection createConnection(ContactId c) {
		if (!running) return null;
		// Get the ISO 3166 code for the caller's country
		String fromIso = callback.getLocalProperties().get("iso3166");
		if (StringUtils.isNullOrEmpty(fromIso)) return null;
		// Get the ISO 3166 code for the callee's country
		TransportProperties properties = callback.getRemoteProperties().get(c);
		if (properties == null) return null;
		String toIso = properties.get("iso3166");
		if (StringUtils.isNullOrEmpty(toIso)) return null;
		// Get the callee's phone number
		String number = properties.get("number");
		if (StringUtils.isNullOrEmpty(number)) return null;
		// Convert the number into direct dialling form
		number = CountryCodes.translate(number, fromIso, toIso);
		if (number == null) return null;
		// Dial the number
		try {
			if (!modem.dial(number)) return null;
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			resetModem();
			return null;
		}
		return new ModemTransportConnection();
	}

	public boolean supportsInvitations() {
		return false;
	}

	public DuplexTransportConnection createInvitationConnection(PseudoRandom r,
			long timeout, boolean alice) {
		throw new UnsupportedOperationException();
	}

	public boolean supportsKeyAgreement() {
		return false;
	}

	public KeyAgreementListener createKeyAgreementListener(
			byte[] commitment) {
		throw new UnsupportedOperationException();
	}

	public DuplexTransportConnection createKeyAgreementConnection(
			byte[] commitment, TransportDescriptor d, long timeout) {
		throw new UnsupportedOperationException();
	}

	public void incomingCallConnected() {
		LOG.info("Incoming call connected");
		callback.incomingConnectionCreated(new ModemTransportConnection());
	}

	private class ModemTransportConnection
			extends AbstractDuplexTransportConnection {

		private ModemTransportConnection() {
			super(ModemPlugin.this);
		}

		@Override
		protected InputStream getInputStream() throws IOException {
			return modem.getInputStream();
		}

		@Override
		protected OutputStream getOutputStream() throws IOException {
			return modem.getOutputStream();
		}

		@Override
		protected void closeConnection(boolean exception) {
			LOG.info("Call disconnected");
			try {
				modem.hangUp();
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				exception = true;
			}
			if (exception) resetModem();
		}
	}
}
