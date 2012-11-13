package net.sf.briar.invitation;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.api.plugins.InvitationConstants.HASH_LENGTH;
import static net.sf.briar.api.plugins.InvitationConstants.INVITATION_TIMEOUT;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.invitation.ConnectionCallback;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;

class AliceConnector extends Thread {

	private static final Logger LOG =
			Logger.getLogger(AliceConnector.class.getName());

	private final DuplexPlugin plugin;
	private final PseudoRandom random;
	private final ConnectionCallback callback;
	private final AtomicBoolean connected, succeeded;
	private final String pluginName;

	AliceConnector(DuplexPlugin plugin, PseudoRandom random,
			ConnectionCallback callback, AtomicBoolean connected,
			AtomicBoolean succeeded) {
		this.plugin = plugin;
		this.random = random;
		this.callback = callback;
		this.connected = connected;
		this.succeeded = succeeded;
		pluginName = plugin.getClass().getName();
	}

	@Override
	public void run() {
		long halfTime = System.currentTimeMillis() + INVITATION_TIMEOUT / 2;
		DuplexTransportConnection conn = makeOutgoingConnection();
		if(conn == null) conn = acceptIncomingConnection(halfTime);
		if(conn == null) return;
		if(LOG.isLoggable(INFO)) LOG.info(pluginName + " connected");
		// Don't proceed with more than one connection
		if(connected.getAndSet(true)) {
			if(LOG.isLoggable(INFO)) LOG.info(pluginName + " redundant");
			tryToClose(conn, false);
			return;
		}
		// FIXME: Carry out the real invitation protocol
		InputStream in;
		try {
			in = conn.getInputStream();
			OutputStream out = conn.getOutputStream();
			byte[] hash = random.nextBytes(HASH_LENGTH);
			out.write(hash);
			out.flush();
			if(LOG.isLoggable(INFO)) LOG.info(pluginName + " sent hash");
			int offset = 0;
			while(offset < hash.length) {
				int read = in.read(hash, offset, hash.length - offset);
				if(read == -1) break;
				offset += read;
			}
			if(offset < HASH_LENGTH) throw new EOFException();
			if(LOG.isLoggable(INFO)) LOG.info(pluginName + " received hash");
			if(LOG.isLoggable(INFO)) LOG.info(pluginName + " succeeded");
			succeeded.set(true);
			callback.connectionEstablished(123456, 123456,
					new ConfirmationSender(out));
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			tryToClose(conn, true);
			return;
		}
		try {
			if(in.read() == 1) callback.codesMatch();
			else callback.codesDoNotMatch();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			tryToClose(conn, true);
			callback.codesDoNotMatch();
		}
	}

	private DuplexTransportConnection makeOutgoingConnection() {
		if(LOG.isLoggable(INFO))
			LOG.info(pluginName + " making outgoing connection");
		return plugin.sendInvitation(random, INVITATION_TIMEOUT / 2);
	}

	private DuplexTransportConnection acceptIncomingConnection(long halfTime) {
		long now = System.currentTimeMillis();
		if(now < halfTime) {
			if(LOG.isLoggable(INFO))
				LOG.info(pluginName + " sleeping until half-time");
			try {
				Thread.sleep(halfTime - now);
			} catch(InterruptedException e) {
				if(LOG.isLoggable(INFO)) LOG.info("Interrupted while sleeping");
				return null;
			}
		}
		if(LOG.isLoggable(INFO))
			LOG.info(pluginName + " accepting incoming connection");
		return plugin.acceptInvitation(random, INVITATION_TIMEOUT / 2);
	}

	private void tryToClose(DuplexTransportConnection conn, boolean exception) {
		try {
			conn.dispose(exception, true);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		}
	}
}