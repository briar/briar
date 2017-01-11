package org.briarproject.bramble.api.plugin.duplex;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.util.StringUtils;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.ID;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PROP_ADDRESS;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PROP_UUID;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.UUID_BYTES;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;

/**
 * Created by Santiago Torres-Arias on 1/10/17.
 */

public abstract class AbstractBluetoothPlugin<C, S> implements DuplexPlugin {

	private static final Logger LOG =
			Logger.getLogger("Halp");

	protected final Executor ioExecutor;
	protected final SecureRandom secureRandom;
	protected final Backoff backoff;
	protected final int maxLatency;
	protected final DuplexPluginCallback callback;
	protected final S ss = null;

	protected volatile boolean running = false;

	protected Runnable pollRunnable = null;

	public AbstractBluetoothPlugin(Executor ioExecutor,
			SecureRandom secureRandom,
			Backoff backoff, int maxLatency,
			DuplexPluginCallback callback) {
		this.ioExecutor = ioExecutor;
		this.secureRandom = secureRandom;
		this.backoff = backoff;
		this.maxLatency = maxLatency;
		this.callback = callback;
	}

	@Override
	public boolean supportsInvitations() {
		return true;
	}

	@Override
	public boolean supportsKeyAgreement() {
		return true;
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	@Override
	public boolean shouldPoll() {
		return true;
	}

	@Override
	public int getPollingInterval() {
		return backoff.getPollingInterval();
	}

	protected String getUuid() {
		String uuid = callback.getLocalProperties().get(PROP_UUID);
		if (uuid == null) {
			byte[] random = new byte[UUID_BYTES];
			secureRandom.nextBytes(random);
			uuid = UUID.nameUUIDFromBytes(random).toString();
			TransportProperties p = new TransportProperties();
			p.put(PROP_UUID, uuid);
			callback.mergeLocalProperties(p);
		}
		return uuid;
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
		// Bluetooth detects dead connections so we don't need keepalives
		return Integer.MAX_VALUE;
	}

	protected String parseAddress(BdfList descriptor) throws FormatException {
		byte[] mac = descriptor.getRaw(1);
		if (mac.length != 6) throw new FormatException();
		return StringUtils.macToString(mac);
	}

	protected void tryToClose(@Nullable S ss) {
		try {
			if (ss != null) close(ss);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} finally {
			callback.transportDisabled();
		}
	}

	protected abstract void close(S ss) throws IOException;

	public void stop() {
		running = false;
		tryToClose(ss);
	}

	@Override
	public void poll(final Collection<ContactId> connected) {
		if (!running) return;
		backoff.increment();
		// Try to connect to known devices in parallel
		Map<ContactId, TransportProperties> remote =
				callback.getRemoteProperties();
		for (Map.Entry<ContactId, TransportProperties> e : remote.entrySet()) {
			final ContactId c = e.getKey();
			if (connected.contains(c)) continue;
			final String address = e.getValue().get(PROP_ADDRESS);
			if (StringUtils.isNullOrEmpty(address)) continue;
			final String uuid = e.getValue().get(PROP_UUID);
			if (StringUtils.isNullOrEmpty(uuid)) continue;
			ioExecutor.execute(returnPollRunnable(address,uuid, c));
		}
	}

	protected abstract Runnable returnPollRunnable(String address, String uuid,
			ContactId c);

	@Override
	public DuplexTransportConnection createConnection(ContactId c) {
		if (!isRunning()) return null;
		TransportProperties p = callback.getRemoteProperties().get(c);
		if (p == null) return null;
		String address = p.get(PROP_ADDRESS);
		if (StringUtils.isNullOrEmpty(address)) return null;
		String uuid = p.get(PROP_UUID);
		if (StringUtils.isNullOrEmpty(uuid)) return null;
		return connectToAddress(address, uuid);
	}

	protected abstract DuplexTransportConnection connectToAddress(String address, String uuid);

	@Override
	public DuplexTransportConnection createKeyAgreementConnection(
			byte[] commitment, BdfList descriptor, long timeout) {
		if (!isRunning()) return null;
		String address;
		try {
			address = parseAddress(descriptor);
		} catch (FormatException e) {
			LOG.info("Invalid address in key agreement descriptor");
			return null;
		}
		// No truncation necessary because COMMIT_LENGTH = 16
		String uuid = UUID.nameUUIDFromBytes(commitment).toString();
		if (LOG.isLoggable(INFO))
			LOG.info("Connecting to key agreement UUID " + uuid);
		return connectToAddress(address, uuid);
	}

}
