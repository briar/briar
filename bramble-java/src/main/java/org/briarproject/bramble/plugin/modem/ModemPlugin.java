package org.briarproject.bramble.plugin.modem;

import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.keyagreement.KeyAgreementListener;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.ConnectionHandler;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.PluginException;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.AbstractDuplexTransportConnection;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.rendezvous.KeyMaterialSource;
import org.briarproject.bramble.api.rendezvous.RendezvousEndpoint;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.StringUtils.isNullOrEmpty;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class ModemPlugin implements DuplexPlugin, Modem.Callback {

	static final TransportId ID =
			new TransportId("org.briarproject.bramble.modem");

	private static final Logger LOG =
			getLogger(ModemPlugin.class.getName());

	private final ModemFactory modemFactory;
	private final SerialPortList serialPortList;
	private final PluginCallback callback;
	private final int maxLatency;
	private final AtomicBoolean used = new AtomicBoolean(false);

	private volatile boolean running = false;
	private volatile Modem modem = null;

	ModemPlugin(ModemFactory modemFactory, SerialPortList serialPortList,
			PluginCallback callback, int maxLatency) {
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
				logException(LOG, WARNING, e);
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
				logException(LOG, WARNING, e);
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
	public void poll(Collection<Pair<TransportProperties, ConnectionHandler>>
			properties) {
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
				logException(LOG, WARNING, e);
			}
		}
		running = false;
		return false;
	}

	@Override
	public DuplexTransportConnection createConnection(TransportProperties p) {
		if (!running) return null;
		// Get the ISO 3166 code for the caller's country
		String fromIso = callback.getLocalProperties().get("iso3166");
		if (isNullOrEmpty(fromIso)) return null;
		// Get the ISO 3166 code for the callee's country
		String toIso = p.get("iso3166");
		if (isNullOrEmpty(toIso)) return null;
		// Get the callee's phone number
		String number = p.get("number");
		if (isNullOrEmpty(number)) return null;
		// Convert the number into direct dialling form
		number = CountryCodes.translate(number, fromIso, toIso);
		if (number == null) return null;
		// Dial the number
		try {
			if (!modem.dial(number)) return null;
		} catch (IOException e) {
			logException(LOG, WARNING, e);
			resetModem();
			return null;
		}
		return new ModemTransportConnection();
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
			byte[] commitment, BdfList descriptor) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean supportsRendezvous() {
		return false;
	}

	@Override
	public RendezvousEndpoint createRendezvousEndpoint(KeyMaterialSource k,
			ConnectionHandler incoming) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void incomingCallConnected() {
		LOG.info("Incoming call connected");
		callback.handleConnection(new ModemTransportConnection());
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
				logException(LOG, WARNING, e);
				exception = true;
			}
			if (exception) resetModem();
		}
	}
}
