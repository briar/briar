package org.briarproject.bramble.plugin.bluetooth;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.crypto.PseudoRandom;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.keyagreement.KeyAgreementConnection;
import org.briarproject.bramble.api.keyagreement.KeyAgreementListener;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.PluginException;
import org.briarproject.bramble.api.plugin.duplex.AbstractBluetoothPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginCallback;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.util.OsUtils;
import org.briarproject.bramble.util.StringUtils;

import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.LocalDevice;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static javax.bluetooth.DiscoveryAgent.GIAC;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.TRANSPORT_ID_BLUETOOTH;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.ID;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PROP_ADDRESS;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PROP_UUID;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.UUID_BYTES;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class BluetoothPlugin<C, S> extends AbstractBluetoothPlugin<C, S> {

	private static final Logger LOG =
			Logger.getLogger(BluetoothPlugin.class.getName());

	private final Semaphore discoverySemaphore = new Semaphore(1);
	private final AtomicBoolean used = new AtomicBoolean(false);

	private volatile StreamConnectionNotifier socket = null;
	private volatile LocalDevice localDevice = null;

	BluetoothPlugin(Executor ioExecutor,
			SecureRandom secureRandom,
			Backoff backoff, int maxLatency,
			DuplexPluginCallback callback) {
		super(ioExecutor, secureRandom, backoff, maxLatency, callback);
	}

	@Override
	public void start() throws PluginException {
		if (used.getAndSet(true)) throw new IllegalStateException();
		// Initialise the Bluetooth stack
		try {
			localDevice = LocalDevice.getLocalDevice();
		} catch (UnsatisfiedLinkError e) {
			// On Linux the user may need to install libbluetooth-dev
			if (OsUtils.isLinux())
				callback.showMessage("BLUETOOTH_INSTALL_LIBS");
			throw new PluginException(e);
		} catch (BluetoothStateException e) {
			throw new PluginException(e);
		}
		if (LOG.isLoggable(INFO))
			LOG.info("Local address " + localDevice.getBluetoothAddress());
		running = true;
		bind();
	}

	private void bind() {
		ioExecutor.execute(new Runnable() {
			@Override
			public void run() {
				if (!running) return;
				// Advertise the Bluetooth address to contacts
				TransportProperties p = new TransportProperties();
				p.put(PROP_ADDRESS, localDevice.getBluetoothAddress());
				callback.mergeLocalProperties(p);
				// Bind a server socket to accept connections from contacts
				String url = makeUrl("localhost", getUuid());
				StreamConnectionNotifier ss;
				try {
					ss = (StreamConnectionNotifier) Connector.open(url);
				} catch (IOException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					return;
				}
				if (!running) {
					tryToClose((S)ss);
					return;
				}
				socket = ss;
				backoff.reset();
				callback.transportEnabled();
				acceptContactConnections(ss);
			}
		});
	}

	private String makeUrl(String address, String uuid) {
		return "btspp://" + address + ":" + uuid + ";name=RFCOMM";
	}
//
//	private void tryToClose(@Nullable StreamConnectionNotifier ss) {
//		try {
//			if (ss != null) ss.close();
//		} catch (IOException e) {
//			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
//		} finally {
//			callback.transportDisabled();
//		}
//	}

	private void acceptContactConnections(StreamConnectionNotifier ss) {
		while (true) {
			StreamConnection s;
			try {
				s = ss.acceptAndOpen();
			} catch (IOException e) {
				// This is expected when the socket is closed
				if (LOG.isLoggable(INFO)) LOG.info(e.toString());
				return;
			}
			backoff.reset();
			callback.incomingConnectionCreated(wrapSocket(s));
			if (!running) return;
		}
	}

	private DuplexTransportConnection wrapSocket(StreamConnection s) {
		return new BluetoothTransportConnection(this, s);
	}
//
//	@Override
//	public void stop() {
//		running = false;
//		tryToClose(socket);
//	}

	@Override
	protected void close(S ss) throws IOException {
		((StreamConnection)ss).close();
	}

	@Override
	public Runnable returnPollRunnable(final String address, final String uuid,
			final ContactId c) {

		return new Runnable() {
			@Override
			public void run() {
				if (!running) return;
				StreamConnection s = connect(makeUrl(address, uuid));
				if (s != null) {
					backoff.reset();
					callback.outgoingConnectionCreated(c, wrapSocket(s));
				}
			}
		};
	}

	private StreamConnection connect(String url) {
		if (LOG.isLoggable(INFO)) LOG.info("Connecting to " + url);
		try {
			StreamConnection s = (StreamConnection) Connector.open(url);
			if (LOG.isLoggable(INFO)) LOG.info("Connected to " + url);
			return s;
		} catch (IOException e) {
			if (LOG.isLoggable(INFO)) LOG.info("Could not connect to " + url);
			return null;
		}
	}

	@Override
	protected DuplexTransportConnection connectToAddress(String address, String uuid) {
			String url = makeUrl(address, uuid);
		StreamConnection s = connect(url);
		if (s == null) return null;
		return new BluetoothTransportConnection(this, s);
	}

	@Override
	public DuplexTransportConnection createInvitationConnection(PseudoRandom r,
			long timeout, boolean alice) {
		if (!running) return null;
		// Use the invitation codes to generate the UUID
		byte[] b = r.nextBytes(UUID_BYTES);
		String uuid = UUID.nameUUIDFromBytes(b).toString();
		String url = makeUrl("localhost", uuid);
		// Make the device discoverable if possible
		makeDeviceDiscoverable();
		// Bind a server socket for receiving invitation connections
		final StreamConnectionNotifier ss;
		try {
			ss = (StreamConnectionNotifier) Connector.open(url);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		}
		if (!running) {
			tryToClose((S)ss);
			return null;
		}
		// Create the background tasks
		CompletionService<StreamConnection> complete =
				new ExecutorCompletionService<>(ioExecutor);
		List<Future<StreamConnection>> futures = new ArrayList<>();
		if (alice) {
			// Return the first connected socket
			futures.add(complete.submit(new ListeningTask(ss)));
			futures.add(complete.submit(new DiscoveryTask(uuid)));
		} else {
			// Return the first socket with readable data
			futures.add(complete.submit(new ReadableTask(
					new ListeningTask(ss))));
			futures.add(complete.submit(new ReadableTask(
					new DiscoveryTask(uuid))));
		}
		StreamConnection chosen = null;
		try {
			Future<StreamConnection> f = complete.poll(timeout, MILLISECONDS);
			if (f == null) return null; // No task completed within the timeout
			chosen = f.get();
			return new BluetoothTransportConnection(this, chosen);
		} catch (InterruptedException e) {
			LOG.info("Interrupted while exchanging invitations");
			Thread.currentThread().interrupt();
			return null;
		} catch (ExecutionException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		} finally {
			// Closing the socket will terminate the listener task
			tryToClose((S)ss);
			closeSockets(futures, chosen);
		}
	}

	private void closeSockets(final List<Future<StreamConnection>> futures,
			@Nullable final StreamConnection chosen) {
		ioExecutor.execute(new Runnable() {
			@Override
			public void run() {
				for (Future<StreamConnection> f : futures) {
					try {
						if (f.cancel(true)) {
							LOG.info("Cancelled task");
						} else {
							StreamConnection s = f.get();
							if (s != null && s != chosen) {
								LOG.info("Closing unwanted socket");
								s.close();
							}
						}
					} catch (InterruptedException e) {
						LOG.info("Interrupted while closing sockets");
						return;
					} catch (ExecutionException | IOException e) {
						if (LOG.isLoggable(INFO)) LOG.info(e.toString());
					}
				}
			}
		});
	}

	@Override
	public KeyAgreementListener createKeyAgreementListener(byte[] commitment) {
		if (!running) return null;
		// No truncation necessary because COMMIT_LENGTH = 16
		String uuid = UUID.nameUUIDFromBytes(commitment).toString();
		if (LOG.isLoggable(INFO)) LOG.info("Key agreement UUID " + uuid);
		String url = makeUrl("localhost", uuid);
		// Make the device discoverable if possible
		makeDeviceDiscoverable();
		// Bind a server socket for receiving invitation connections
		final StreamConnectionNotifier ss;
		try {
			ss = (StreamConnectionNotifier) Connector.open(url);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		}
		if (!running) {
			tryToClose((S)ss);
			return null;
		}
		BdfList descriptor = new BdfList();
		descriptor.add(TRANSPORT_ID_BLUETOOTH);
		String address = localDevice.getBluetoothAddress();
		descriptor.add(StringUtils.macToBytes(address));
		return new BluetoothKeyAgreementListener(descriptor, ss);
	}

	private void makeDeviceDiscoverable() {
		// Try to make the device discoverable (requires root on Linux)
		try {
			localDevice.setDiscoverable(GIAC);
		} catch (BluetoothStateException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private class DiscoveryTask implements Callable<StreamConnection> {

		private final String uuid;

		private DiscoveryTask(String uuid) {
			this.uuid = uuid;
		}

		@Override
		public StreamConnection call() throws Exception {
			// Repeat discovery until we connect or get interrupted
			DiscoveryAgent discoveryAgent = localDevice.getDiscoveryAgent();
			while (true) {
				if (!discoverySemaphore.tryAcquire())
					throw new Exception("Discovery is already in progress");
				try {
					InvitationListener listener =
							new InvitationListener(discoveryAgent, uuid);
					discoveryAgent.startInquiry(GIAC, listener);
					String url = listener.waitForUrl();
					if (url != null) {
						StreamConnection s = connect(url);
						if (s != null) {
							LOG.info("Outgoing connection");
							return s;
						}
					}
				} finally {
					discoverySemaphore.release();
				}
			}
		}
	}

	private static class ListeningTask implements Callable<StreamConnection> {

		private final StreamConnectionNotifier serverSocket;

		private ListeningTask(StreamConnectionNotifier serverSocket) {
			this.serverSocket = serverSocket;
		}

		@Override
		public StreamConnection call() throws Exception {
			StreamConnection s = serverSocket.acceptAndOpen();
			LOG.info("Incoming connection");
			return s;
		}
	}

	private static class ReadableTask implements Callable<StreamConnection> {

		private final Callable<StreamConnection> connectionTask;

		private ReadableTask(Callable<StreamConnection> connectionTask) {
			this.connectionTask = connectionTask;
		}

		@Override
		public StreamConnection call() throws Exception {
			StreamConnection s = connectionTask.call();
			InputStream in = s.openInputStream();
			while (in.available() == 0) {
				LOG.info("Waiting for data");
				Thread.sleep(1000);
			}
			LOG.info("Data available");
			return s;
		}
	}

	private class BluetoothKeyAgreementListener extends KeyAgreementListener {

		private final StreamConnectionNotifier ss;

		private BluetoothKeyAgreementListener(BdfList descriptor,
				StreamConnectionNotifier ss) {
			super(descriptor);
			this.ss = ss;
		}

		@Override
		public Callable<KeyAgreementConnection> listen() {
			return new Callable<KeyAgreementConnection>() {
				@Override
				public KeyAgreementConnection call() throws Exception {
					StreamConnection s = ss.acceptAndOpen();
					if (LOG.isLoggable(INFO))
						LOG.info(ID.getString() + ": Incoming connection");
					return new KeyAgreementConnection(
							new BluetoothTransportConnection(
									BluetoothPlugin.this, s), ID);
				}
			};
		}

		@Override
		public void close() {
			try {
				ss.close();
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}
}
