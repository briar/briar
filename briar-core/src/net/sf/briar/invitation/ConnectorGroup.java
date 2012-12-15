package net.sf.briar.invitation;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.api.plugins.InvitationConstants.CONFIRMATION_TIMEOUT;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.invitation.InvitationListener;
import net.sf.briar.api.invitation.InvitationManager;
import net.sf.briar.api.invitation.InvitationState;
import net.sf.briar.api.invitation.InvitationTask;
import net.sf.briar.api.plugins.PluginManager;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.WriterFactory;

/** A task consisting of one or more parallel connection attempts. */
class ConnectorGroup extends Thread implements InvitationTask {

	private static final Logger LOG =
			Logger.getLogger(ConnectorGroup.class.getName());

	private final InvitationManager invitationManager;
	private final CryptoComponent crypto;
	private final ReaderFactory readerFactory;
	private final WriterFactory writerFactory;
	private final Clock clock;
	private final PluginManager pluginManager;
	private final int handle, localInvitationCode, remoteInvitationCode;
	private final Collection<InvitationListener> listeners;
	private final AtomicBoolean connected;
	private final CountDownLatch localConfirmationLatch;

	/*
	 * All of the following require locking: this. We don't want to call the
	 * listeners with a lock held, but we need to avoid a race condition in
	 * addListener(), so the state that's accessed there after calling
	 * listeners.add() must be guarded by a lock.
	 */
	private int localConfirmationCode = -1, remoteConfirmationCode = -1;
	private boolean connectionFailed = false;
	private boolean localCompared = false, remoteCompared = false;
	private boolean localMatched = false, remoteMatched = false;

	ConnectorGroup(InvitationManager invitationManager, CryptoComponent crypto,
			ReaderFactory readerFactory, WriterFactory writerFactory,
			Clock clock, PluginManager pluginManager, int handle,
			int localInvitationCode, int remoteInvitationCode) {
		super("ConnectorGroup");
		this.invitationManager = invitationManager;
		this.crypto = crypto;
		this.readerFactory = readerFactory;
		this.writerFactory = writerFactory;
		this.clock = clock;
		this.pluginManager = pluginManager;
		this.handle = handle;
		this.localInvitationCode = localInvitationCode;
		this.remoteInvitationCode = remoteInvitationCode;
		listeners = new CopyOnWriteArrayList<InvitationListener>();
		connected = new AtomicBoolean(false);
		localConfirmationLatch = new CountDownLatch(1);
	}

	public int getHandle() {
		return handle;
	}

	public synchronized InvitationState addListener(InvitationListener l) {
		listeners.add(l);
		return new InvitationState(localInvitationCode, remoteInvitationCode,
				localConfirmationCode, remoteConfirmationCode, connectionFailed,
				localCompared, remoteCompared, localMatched, remoteMatched);
	}

	public void removeListener(InvitationListener l) {
		listeners.remove(l);
	}

	public void connect() {
		start();
	}

	@Override
	public void run() {
		// Add this task to the manager
		invitationManager.putTask(handle, this);
		// Start the connection threads
		final Collection<Connector> connectors = new ArrayList<Connector>();
		// Alice is the party with the smaller invitation code
		if(localInvitationCode < remoteInvitationCode) {
			for(DuplexPlugin plugin : pluginManager.getInvitationPlugins()) {
				Connector c = new AliceConnector(crypto, readerFactory,
						writerFactory, clock, this, plugin, localInvitationCode,
						remoteInvitationCode);
				connectors.add(c);
				c.start();
			}
		} else {
			for(DuplexPlugin plugin: pluginManager.getInvitationPlugins()) {
				Connector c = (new BobConnector(crypto, readerFactory,
						writerFactory, clock, this, plugin, localInvitationCode,
						remoteInvitationCode));
				connectors.add(c);
				c.start();
			}
		}
		// Wait for the connection threads to finish
		try {
			for(Connector c : connectors) c.join();
		} catch(InterruptedException e) {
			if(LOG.isLoggable(WARNING))
				LOG.warning("Interrupted while waiting for connectors");
		}
		// If none of the threads connected, inform the listeners
		if(!connected.get()) {
			synchronized(this) {
				connectionFailed = true;
			}
			for(InvitationListener l : listeners) l.connectionFailed();
		}
		// Remove this task from the manager
		invitationManager.removeTask(handle);
	}

	public void localConfirmationSucceeded() {
		synchronized(this) {
			localCompared = true;
			localMatched = true;
		}
		localConfirmationLatch.countDown();
	}

	public void localConfirmationFailed() {
		synchronized(this) {
			localCompared = true;
		}
		localConfirmationLatch.countDown();
	}

	boolean getAndSetConnected() {
		return connected.getAndSet(true);
	}

	void connectionSucceeded(int localCode, int remoteCode) {
		synchronized(this) {
			localConfirmationCode = localCode;
			remoteConfirmationCode = remoteCode;
		}
		for(InvitationListener l : listeners)
			l.connectionSucceeded(localCode, remoteCode);
	}

	void remoteConfirmationSucceeded() {
		synchronized(this) {
			remoteCompared = true;
			remoteMatched = true;
		}
		for(InvitationListener l : listeners) l.remoteConfirmationSucceeded();
	}

	void remoteConfirmationFailed() {
		synchronized(this) {
			remoteCompared = true;
		}
		for(InvitationListener l : listeners) l.remoteConfirmationFailed();
	}

	boolean waitForLocalConfirmationResult() throws InterruptedException {
		localConfirmationLatch.await(CONFIRMATION_TIMEOUT, MILLISECONDS);
		synchronized(this) {
			return localMatched;
		}
	}
}
