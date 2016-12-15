package org.briarproject.bramble.plugin.modem;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.crypto.PseudoRandom;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.keyagreement.KeyAgreementListener;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginException;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.AbstractDuplexTransportConnection;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginCallback;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class ModemPlugin implements DuplexPlugin, Modem.Callback {

	static final TransportId ID =
			new TransportId("org.briarproject.bramble.modem");

	private static final Logger LOG =
			Logger.getLogger(ModemPlugin.class.getName());

	private final ModemFactory modemFactory;
	private final SerialPortList serialPortList;
	private final DuplexPluginCallback callback;
	private final int maxLatency;
	private final AtomicBoolean used = new AtomicBoolean(false);

	private volatile boolean running = false;
	private volatile Modem modem = null;

	ModemPlugin(ModemFactory modemFactory, SerialPortList serialPortList,
			DuplexPluginCallback callback, int maxLatency) {
		this.modemFactory = modemFactory;
		this.serialPortList = serialPortList;
		this.callback = callback;
		this.maxLatency = maxLatency;
	}

	@Override
	public TransportId getId() {
		return ID;
	}

	@Override
	public int getMaxLatency() {
		return maxLatency;
	}

	@Override
	public int getMaxIdleTime() {
		// FIXME: Do we need keepalives for this transport?
		return Integer.MAX_VALUE;
	}

	@Override
	public void start() throws PluginException {
		if (used.getAndSet(true)) throw new IllegalStateException();
		for (String portName : serialPortList.getPortNames()) {
			if (LOG.isLoggable(INFO))
				LOG.info("Trying to initialise modem on " + portName);
			modem = modemFactory.createModem(this, portName);
			try {
				if (!modem.start()) continue;
				if (LOG.isLoggable(INFO))
					LOG.info("Initialised modem on " + portName);
				running = true;
				return;
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
		throw new PluginException();
	}

	@Override
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

	@Override
	public boolean isRunning() {
		return running;
	}

	@Override
	public boolean shouldPoll() {
		return false;
	}

	@Override
	public int getPollingInterval() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void poll(Collection<ContactId> connected) {
		throw new UnsupportedOperationException();
	}

	private boolean resetModem() {
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

	@Override
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

	@Override
	public boolean supportsInvitations() {
		return false;
	}

	@Override
	public DuplexTransportConnection createInvitationConnection(PseudoRandom r,
			long timeout, boolean alice) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean supportsKeyAgreement() {
		return false;
	}

	@Override
	public KeyAgreementListener createKeyAgreementListener(byte[] commitment) {
		throw new UnsupportedOperationException();
	}

	@Override
	public DuplexTransportConnection createKeyAgreementConnection(
			byte[] commitment, BdfList descriptor, long timeout) {
		throw new UnsupportedOperationException();
	}

	@Override
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
