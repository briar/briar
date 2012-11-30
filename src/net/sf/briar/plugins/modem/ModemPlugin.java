package net.sf.briar.plugins.modem;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

import jssc.SerialPortList;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.util.StringUtils;

class ModemPlugin implements DuplexPlugin, Modem.Callback {

	static final byte[] TRANSPORT_ID =
			StringUtils.fromHexString("8f573867bedf54884b5868ee5d902832" +
					"ee5e522da84d0d431712bd672fbd2f79" +
					"262d27b93879b94ee9afbb80e7fc87fb");
	static final TransportId ID = new TransportId(TRANSPORT_ID);

	private static final Logger LOG =
			Logger.getLogger(ModemPlugin.class.getName());

	private final Executor pluginExecutor;
	private final ModemFactory modemFactory;
	private final DuplexPluginCallback callback;
	private final long pollingInterval;
	private final Semaphore polling;

	private volatile boolean running = false;
	private volatile Modem modem = null;

	ModemPlugin(@PluginExecutor Executor pluginExecutor,
			ModemFactory modemFactory, DuplexPluginCallback callback,
			long pollingInterval) {
		this.pluginExecutor = pluginExecutor;
		this.modemFactory = modemFactory;
		this.callback = callback;
		this.pollingInterval = pollingInterval;
		polling = new Semaphore(1);
	}

	public TransportId getId() {
		return ID;
	}

	public String getName() {
		return "MODEM_PLUGIN_NAME";
	}

	public boolean start() {
		for(String portName : SerialPortList.getPortNames()) {
			modem = modemFactory.createModem(this, portName);
			try {
				modem.init();
				if(LOG.isLoggable(INFO))
					LOG.info("Initialised modem on " + portName);
				running = true;
				return true;
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			}
		}
		return false;
	}

	public void stop() {
		running = false;
	}

	private boolean resetModem() {
		if(!running) return false;
		for(String portName : SerialPortList.getPortNames()) {
			modem = modemFactory.createModem(this, portName);
			try {
				modem.init();
				if(LOG.isLoggable(INFO))
					LOG.info("Initialised modem on " + portName);
				return true;
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			}
		}
		running = false;
		return false;
	}

	public boolean shouldPoll() {
		return true;
	}

	public long getPollingInterval() {
		return pollingInterval;
	}

	public void poll(Collection<ContactId> connected) {
		if(!connected.isEmpty()) return; // One at a time please
		pluginExecutor.execute(new Runnable() {
			public void run() {
				poll();
			}
		});
	}

	private void poll() {
		if(!running) return;
		if(!polling.tryAcquire()) {
			if(LOG.isLoggable(INFO))
				LOG.info("Previous poll still in progress");
			return;
		}
		// Call contacts one at a time in a random order
		Map<ContactId, TransportProperties> remote =
				callback.getRemoteProperties();
		List<ContactId> contacts = new ArrayList<ContactId>(remote.keySet());
		Collections.shuffle(contacts);
		Iterator<ContactId> it = contacts.iterator();
		while(it.hasNext() && running) {
			ContactId c = it.next();
			String number = remote.get(c).get("number");
			if(number == null) continue;
			try {
				if(!modem.dial(number)) continue;
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
				if(resetModem()) continue;
				else break;
			}
			if(LOG.isLoggable(INFO)) LOG.info("Outgoing call connected");
			ModemTransportConnection conn = new ModemTransportConnection();
			callback.outgoingConnectionCreated(c, conn);
			try {
				conn.waitForDisposal();
			} catch(InterruptedException e) {
				if(LOG.isLoggable(WARNING))
					LOG.warning("Interrupted while polling");
				Thread.currentThread().interrupt();
				break;
			}
		}
		polling.release();
	}

	public DuplexTransportConnection createConnection(ContactId c) {
		if(!running) return null;
		final Map<ContactId, TransportProperties> remote =
				callback.getRemoteProperties();
		String number = remote.get(c).get("number");
		if(number == null) return null;
		try {
			if(!modem.dial(number)) return null;
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			resetModem();
			return null;
		}
		return new ModemTransportConnection();
	}

	public boolean supportsInvitations() {
		return false;
	}

	public DuplexTransportConnection sendInvitation(PseudoRandom r,
			long timeout) {
		throw new UnsupportedOperationException();
	}

	public DuplexTransportConnection acceptInvitation(PseudoRandom r,
			long timeout) {
		throw new UnsupportedOperationException();
	}

	public void incomingCallConnected() {
		if(LOG.isLoggable(INFO)) LOG.info("Incoming call connected");
		callback.incomingConnectionCreated(new ModemTransportConnection());
	}

	private class ModemTransportConnection
	implements DuplexTransportConnection {

		private final CountDownLatch finished = new CountDownLatch(1);

		public InputStream getInputStream() {
			return modem.getInputStream();
		}

		public OutputStream getOutputStream() {
			return modem.getOutputStream();
		}

		public boolean shouldFlush() {
			return true;
		}

		public void dispose(boolean exception, boolean recognised) {
			if(LOG.isLoggable(INFO)) LOG.info("Call disconnected");
			try {
				modem.hangUp();
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
				exception = true;
			}
			if(exception) resetModem();
			finished.countDown();
		}

		private void waitForDisposal() throws InterruptedException {
			finished.await();
		}
	}
}
