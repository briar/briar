package net.sf.briar.plugins.tor;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.util.StringUtils;

import org.silvertunnel.netlib.api.NetAddress;
import org.silvertunnel.netlib.api.NetFactory;
import org.silvertunnel.netlib.api.NetLayer;
import org.silvertunnel.netlib.api.NetLayerIDs;
import org.silvertunnel.netlib.api.NetServerSocket;
import org.silvertunnel.netlib.api.NetSocket;
import org.silvertunnel.netlib.api.util.TcpipNetAddress;
import org.silvertunnel.netlib.layer.tor.TorHiddenServicePortPrivateNetAddress;
import org.silvertunnel.netlib.layer.tor.TorHiddenServicePrivateNetAddress;
import org.silvertunnel.netlib.layer.tor.TorNetLayerUtil;
import org.silvertunnel.netlib.layer.tor.util.Encryption;
import org.silvertunnel.netlib.layer.tor.util.RSAKeyPair;

class TorPlugin implements DuplexPlugin {

	static final byte[] TRANSPORT_ID =
			StringUtils.fromHexString("f264721575cb7ee710772f35abeb3db4"
					+ "a91f474e14de346be296c2efc99effdd"
					+ "f35921e6ed87a25c201f044da4767981");
	static final TransportId ID = new TransportId(TRANSPORT_ID);

	private static final Logger LOG =
			Logger.getLogger(TorPlugin.class.getName());

	private final Executor pluginExecutor;
	private final DuplexPluginCallback callback;
	private final long pollingInterval;

	private boolean running = false, connected = false; // Locking: this
	private NetLayer netLayer = null; // Locking: this
	private NetServerSocket socket = null; // Locking: this

	TorPlugin(@PluginExecutor Executor pluginExecutor,
			DuplexPluginCallback callback, long pollingInterval) {
		this.pluginExecutor = pluginExecutor;
		this.callback = callback;
		this.pollingInterval = pollingInterval;
	}

	public TransportId getId() {
		return ID;
	}

	public String getName() {
		return "TOR_PLUGIN_NAME";
	}

	public boolean start() {
		synchronized(this) {
			running = true;
		}
		pluginExecutor.execute(new Runnable() {
			public void run() {
				bind();
			}
		});
		return true;
	}

	private void bind() {
		// Connect to Tor
		NetFactory netFactory = NetFactory.getInstance();
		NetLayer nl = netFactory.getNetLayerById(NetLayerIDs.TOR);
		if(LOG.isLoggable(INFO)) LOG.info("Waiting for net layer to be ready");
		nl.waitUntilReady();
		if(LOG.isLoggable(INFO)) LOG.info("Net layer is ready");
		synchronized(this) {
			if(!running) {
				tryToClear(nl);
				return;
			}
			netLayer = nl;
			connected = true;
			notifyAll();
		}
		// If we're configured not to create a hidden service, return
		TransportConfig c = callback.getConfig();
		String noHiddenService = c.get("noHiddenService");
		if(!StringUtils.isNullOrEmpty(noHiddenService)) {
			if(LOG.isLoggable(INFO)) LOG.info("Not creating hidden service");
			TransportProperties p = new TransportProperties();
			p.put("onion", null);
			callback.mergeLocalProperties(p);
			return;
		}
		// Retrieve the hidden service address, or create one if necessary
		TorHiddenServicePrivateNetAddress addr;
		TorNetLayerUtil util = TorNetLayerUtil.getInstance();
		String privateKey = c.get("privateKey");
		if(StringUtils.isNullOrEmpty(privateKey)) {
			if(LOG.isLoggable(INFO))
				LOG.info("Creating hidden service address");
			addr = createHiddenServiceAddress(util);
		} else {
			if(LOG.isLoggable(INFO))
				LOG.info("Parsing hidden service address");
			try {
				addr = util.parseTorHiddenServicePrivateNetAddressFromStrings(
						privateKey, "", false);
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				addr = createHiddenServiceAddress(util);
			}
		}
		TorHiddenServicePortPrivateNetAddress addrPort =
				new TorHiddenServicePortPrivateNetAddress(addr, 80);
		// Publish the hidden service
		NetServerSocket ss;
		if(LOG.isLoggable(INFO)) LOG.info("Publishing hidden service");
		try {
			ss = nl.createNetServerSocket(null, addrPort);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return;
		}
		synchronized(this) {
			if(!running) {
				tryToClose(ss);
				return;
			}
			socket = ss;
		}
		String onion = addr.getPublicOnionHostname();
		if(LOG.isLoggable(INFO)) LOG.info("Listening on " + onion);
		TransportProperties p = callback.getLocalProperties();
		p.put("onion", onion);
		callback.mergeLocalProperties(p);
		acceptContactConnections(ss);
	}

	private TorHiddenServicePrivateNetAddress createHiddenServiceAddress(
			TorNetLayerUtil util) {
		TorHiddenServicePrivateNetAddress addr =
				util.createNewTorHiddenServicePrivateNetAddress();
		RSAKeyPair keyPair = addr.getKeyPair();
		String privateKey = Encryption.getPEMStringFromRSAKeyPair(keyPair);
		TransportConfig c = new TransportConfig();
		c.put("privateKey", privateKey);
		callback.mergeConfig(c);
		return addr;
	}

	private void tryToClear(NetLayer nl) {
		try {
			nl.clear();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private void tryToClose(NetServerSocket ss) {
		try {
			ss.close();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private void acceptContactConnections(NetServerSocket ss) {
		while(true) {
			NetSocket s;
			try {
				s = ss.accept();
			} catch(IOException e) {
				// This is expected when the socket is closed
				if(LOG.isLoggable(INFO)) LOG.log(INFO, e.toString(), e);
				tryToClose(ss);
				return;
			}
			TorTransportConnection conn = new TorTransportConnection(s);
			callback.incomingConnectionCreated(conn);
			synchronized(this) {
				if(!running) return;
			}
		}
	}

	public synchronized void stop() throws IOException {
		if(netLayer != null) {
			netLayer.clear();
			netLayer = null;
		}
		if(socket != null) {
			tryToClose(socket);
			socket = null;
		}
		running = false;
		connected = false;
		notifyAll();
	}

	public boolean shouldPoll() {
		return true;
	}

	public long getPollingInterval() {
		return pollingInterval;
	}

	public void poll(Collection<ContactId> connected) {
		synchronized(this) {
			if(!running) return;
		}
		Map<ContactId, TransportProperties> remote =
				callback.getRemoteProperties();
		for(final ContactId c : remote.keySet()) {
			if(connected.contains(c)) continue;
			pluginExecutor.execute(new Runnable() {
				public void run() {
					connectAndCallBack(c);
				}
			});
		}
	}

	private void connectAndCallBack(ContactId c) {
		DuplexTransportConnection d = createConnection(c);
		if(d != null) callback.outgoingConnectionCreated(c, d);
	}

	public boolean supportsInvitations() {
		return false;
	}

	public DuplexTransportConnection createConnection(ContactId c) {
		NetLayer nl;
		synchronized(this) {
			while(!connected) {
				if(!running) return null;
				if(LOG.isLoggable(INFO))
					LOG.info("Waiting for net layer before connecting");
				try {
					wait();
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting to connect");
					Thread.currentThread().interrupt();
					return null;
				}
			}
			nl = netLayer;
		}
		TransportProperties p = callback.getRemoteProperties().get(c);
		if(p == null) return null;
		String onion = p.get("onion");
		if(StringUtils.isNullOrEmpty(onion)) return null;
		NetAddress addr = new TcpipNetAddress(onion, 80);
		try {
			if(LOG.isLoggable(INFO)) LOG.info("Connecting to hidden service");
			NetSocket s = nl.createNetSocket(null, null, addr);
			if(LOG.isLoggable(INFO)) LOG.info("Connected to hidden service");
			return new TorTransportConnection(s);
		} catch(IOException e) {
			if(LOG.isLoggable(INFO)) LOG.log(INFO, e.toString(), e);
			return null;
		}
	}

	public DuplexTransportConnection sendInvitation(PseudoRandom r,
			long timeout) {
		throw new UnsupportedOperationException();
	}

	public DuplexTransportConnection acceptInvitation(PseudoRandom r,
			long timeout) {
		throw new UnsupportedOperationException();
	}
}
