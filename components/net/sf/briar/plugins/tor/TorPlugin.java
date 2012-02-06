package net.sf.briar.plugins.tor;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.util.StringUtils;

import org.silvertunnel.netlib.api.NetFactory;
import org.silvertunnel.netlib.api.NetLayer;
import org.silvertunnel.netlib.api.NetLayerIDs;
import org.silvertunnel.netlib.api.NetServerSocket;
import org.silvertunnel.netlib.api.NetSocket;
import org.silvertunnel.netlib.api.util.TcpipNetAddress;
import org.silvertunnel.netlib.layer.tor.TorHiddenServicePortPrivateNetAddress;
import org.silvertunnel.netlib.layer.tor.TorHiddenServicePrivateNetAddress;
import org.silvertunnel.netlib.layer.tor.TorNetLayerUtil;
import org.silvertunnel.netlib.layer.tor.TorNetServerSocket;
import org.silvertunnel.netlib.layer.tor.util.Encryption;
import org.silvertunnel.netlib.layer.tor.util.RSAKeyPair;

class TorPlugin implements DuplexPlugin {

	public static final byte[] TRANSPORT_ID =
		StringUtils.fromHexString("f264721575cb7ee710772f35abeb3db4"
				+ "a91f474e14de346be296c2efc99effdd");

	private static final TransportId ID = new TransportId(TRANSPORT_ID);
	private static final Logger LOG =
		Logger.getLogger(TorPlugin.class.getName());

	private final Executor pluginExecutor;
	private final DuplexPluginCallback callback;
	private final long pollingInterval;

	private boolean running = false; // Locking: this
	private TorNetServerSocket socket = null; // Locking: this

	TorPlugin(@PluginExecutor Executor pluginExecutor,
			DuplexPluginCallback callback, long pollingInterval) {
		this.pluginExecutor = pluginExecutor;
		this.callback = callback;
		this.pollingInterval = pollingInterval;
	}

	public TransportId getId() {
		return ID;
	}

	public void start() throws IOException {
		synchronized(this) {
			running = true;
		}
		pluginExecutor.execute(new Runnable() {
			public void run() {
				bind();
			}
		});
	}

	private void bind() {
		// Retrieve the hidden service address, or create one if necessary
		TorHiddenServicePrivateNetAddress addr;
		TransportConfig c = callback.getConfig();
		String privateKey = c.get("privateKey");
		if(privateKey == null) {
			addr = createHiddenServiceAddress(c);
		} else {
			TorNetLayerUtil util = TorNetLayerUtil.getInstance();
			try {
				addr = util.parseTorHiddenServicePrivateNetAddressFromStrings(
						privateKey, "", false);
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
				addr = createHiddenServiceAddress(c);
			}
		}
		TorHiddenServicePortPrivateNetAddress addrPort =
			new TorHiddenServicePortPrivateNetAddress(addr, 80);
		// Connect to Tor
		NetFactory netFactory = NetFactory.getInstance();
		NetLayer netLayer = netFactory.getNetLayerById(NetLayerIDs.TOR);
		netLayer.waitUntilReady();
		// Publish the hidden service
		TorNetServerSocket ss;
		try {
			ss = (TorNetServerSocket) netLayer.createNetServerSocket(null,
					addrPort);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
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
		if(LOG.isLoggable(Level.INFO)) LOG.info("Listening on " + onion);
		TransportProperties p = callback.getLocalProperties();
		p.put("onion", onion);
		callback.setLocalProperties(p);
		acceptContactConnections(ss);
	}

	private TorHiddenServicePrivateNetAddress createHiddenServiceAddress(
			TransportConfig c) {
		TorNetLayerUtil util = TorNetLayerUtil.getInstance();
		TorHiddenServicePrivateNetAddress addr =
			util.createNewTorHiddenServicePrivateNetAddress();
		RSAKeyPair keyPair = addr.getKeyPair();
		String privateKey = Encryption.getPEMStringFromRSAKeyPair(keyPair);
		c.put("privateKey", privateKey);
		callback.setConfig(c);
		return addr;
	}

	private void tryToClose(NetServerSocket ss) {
		try {
			ss.close();
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
		}
	}

	private void acceptContactConnections(NetServerSocket ss) {
		while(true) {
			NetSocket s;
			try {
				s = ss.accept();
			} catch(IOException e) {
				// This is expected when the socket is closed
				if(LOG.isLoggable(Level.INFO)) LOG.info(e.toString());
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
		running = false;
		if(socket != null) {
			tryToClose(socket);
			socket = null;
		}
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
		synchronized(this) {
			if(!running) return null;
		}
		TransportProperties p = callback.getRemoteProperties().get(c);
		if(p == null) return null;
		String onion = p.get("onion");
		String portString = p.get("port");
		if(onion == null || portString == null) return null;
		try {
			int port = Integer.parseInt(portString);
			TcpipNetAddress addr = new TcpipNetAddress(onion, port);
			NetFactory netFactory = NetFactory.getInstance();
			NetLayer netLayer = netFactory.getNetLayerById(NetLayerIDs.TOR);
			netLayer.waitUntilReady();
			NetSocket s = netLayer.createNetSocket(null, null, addr);
			return new TorTransportConnection(s);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.INFO)) LOG.info(e.toString());
			return null;
		}
	}

	public DuplexTransportConnection sendInvitation(int code, long timeout) {
		throw new UnsupportedOperationException();
	}

	public DuplexTransportConnection acceptInvitation(int code, long timeout) {
		throw new UnsupportedOperationException();
	}
}
