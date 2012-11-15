package net.sf.briar.invitation;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.api.plugins.InvitationConstants.CONFIRMATION_TIMEOUT;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import net.sf.briar.api.invitation.InvitationListener;
import net.sf.briar.api.invitation.InvitationManager;
import net.sf.briar.api.invitation.InvitationState;
import net.sf.briar.api.invitation.InvitationTask;

/** A task consisting of one or more parallel connection attempts. */
class ConnectorGroup implements InvitationTask {

	private static final Logger LOG =
			Logger.getLogger(ConnectorGroup.class.getName());

	private final InvitationManager manager;
	private final int handle, localInvitationCode, remoteInvitationCode;
	private final Collection<Connector> connectors;
	private final Collection<InvitationListener> listeners;
	private final AtomicBoolean connected;
	private final CountDownLatch localConfirmationLatch;

	/*
	 * All of the following are locking: this. We don't want to call the
	 * listeners with a lock held, but we need to avoid a race condition in
	 * addListener(), so the state that's accessed there after calling
	 * listeners.add() must be guarded by a lock.
	 */
	private int localConfirmationCode = -1, remoteConfirmationCode = -1;
	private boolean connectionFailed = false;
	private boolean localCompared = false, remoteCompared = false;
	private boolean localMatched = false, remoteMatched = false;

	ConnectorGroup(InvitationManager manager, int handle,
			int localInvitationCode, int remoteInvitationCode) {
		this.manager = manager;
		this.handle = handle;
		this.localInvitationCode = localInvitationCode;
		this.remoteInvitationCode = remoteInvitationCode;
		connectors = new CopyOnWriteArrayList<Connector>();
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

	// FIXME: The task isn't removed from the manager unless this is called
	public void connect() {
		for(Connector c : connectors) c.start();
		new Thread() {
			@Override
			public void run() {
				try {
					for(Connector c : connectors) c.join();
				} catch(InterruptedException e) {
					if(LOG.isLoggable(WARNING))
						LOG.warning("Interrupted while waiting for connectors");
				}
				if(!connected.get()) {
					synchronized(ConnectorGroup.this) {
						connectionFailed = true;
					}
					for(InvitationListener l : listeners) l.connectionFailed();
				}
				manager.removeTask(handle);
			}
		}.start();
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

	void addConnector(Connector c) {
		connectors.add(c);
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
