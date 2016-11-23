package org.briarproject.bramble.invitation;

import org.briarproject.bramble.api.contact.ContactExchangeListener;
import org.briarproject.bramble.api.contact.ContactExchangeTask;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.PseudoRandom;
import org.briarproject.bramble.api.data.BdfReaderFactory;
import org.briarproject.bramble.api.data.BdfWriterFactory;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.invitation.InvitationListener;
import org.briarproject.bramble.api.invitation.InvitationState;
import org.briarproject.bramble.api.invitation.InvitationTask;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.invitation.InvitationConstants.CONFIRMATION_TIMEOUT;

/**
 * A task consisting of one or more parallel connection attempts.
 */
@MethodsNotNullByDefault
@ParametersNotNullByDefault
class ConnectorGroup extends Thread implements InvitationTask,
		ContactExchangeListener {

	private static final Logger LOG =
			Logger.getLogger(ConnectorGroup.class.getName());

	private final CryptoComponent crypto;
	private final BdfReaderFactory bdfReaderFactory;
	private final BdfWriterFactory bdfWriterFactory;
	private final ContactExchangeTask contactExchangeTask;
	private final IdentityManager identityManager;
	private final PluginManager pluginManager;
	private final int localInvitationCode, remoteInvitationCode;
	private final Collection<InvitationListener> listeners;
	private final AtomicBoolean connected;
	private final CountDownLatch localConfirmationLatch;
	private final Lock lock = new ReentrantLock();

	// The following are locking: lock
	private int localConfirmationCode = -1, remoteConfirmationCode = -1;
	private boolean connectionFailed = false;
	private boolean localCompared = false, remoteCompared = false;
	private boolean localMatched = false, remoteMatched = false;
	private String remoteName = null;

	ConnectorGroup(CryptoComponent crypto, BdfReaderFactory bdfReaderFactory,
			BdfWriterFactory bdfWriterFactory,
			ContactExchangeTask contactExchangeTask,
			IdentityManager identityManager, PluginManager pluginManager,
			int localInvitationCode, int remoteInvitationCode) {
		super("ConnectorGroup");
		this.crypto = crypto;
		this.bdfReaderFactory = bdfReaderFactory;
		this.bdfWriterFactory = bdfWriterFactory;
		this.contactExchangeTask = contactExchangeTask;
		this.identityManager = identityManager;
		this.pluginManager = pluginManager;
		this.localInvitationCode = localInvitationCode;
		this.remoteInvitationCode = remoteInvitationCode;
		listeners = new CopyOnWriteArrayList<InvitationListener>();
		connected = new AtomicBoolean(false);
		localConfirmationLatch = new CountDownLatch(1);
	}

	@Override
	public InvitationState addListener(InvitationListener l) {
		lock.lock();
		try {
			listeners.add(l);
			return new InvitationState(localInvitationCode,
					remoteInvitationCode, localConfirmationCode,
					remoteConfirmationCode, connected.get(), connectionFailed,
					localCompared, remoteCompared, localMatched, remoteMatched,
					remoteName);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void removeListener(InvitationListener l) {
		listeners.remove(l);
	}

	@Override
	public void connect() {
		start();
	}

	@Override
	public void run() {
		LocalAuthor localAuthor;
		// Load the local pseudonym
		try {
			localAuthor = identityManager.getLocalAuthor();
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			lock.lock();
			try {
				connectionFailed = true;
			} finally {
				lock.unlock();
			}
			for (InvitationListener l : listeners) l.connectionFailed();
			return;
		}
		// Start the connection threads
		Collection<Connector> connectors = new ArrayList<Connector>();
		// Alice is the party with the smaller invitation code
		if (localInvitationCode < remoteInvitationCode) {
			for (DuplexPlugin plugin : pluginManager.getInvitationPlugins()) {
				Connector c = createAliceConnector(plugin, localAuthor);
				connectors.add(c);
				c.start();
			}
		} else {
			for (DuplexPlugin plugin : pluginManager.getInvitationPlugins()) {
				Connector c = createBobConnector(plugin, localAuthor);
				connectors.add(c);
				c.start();
			}
		}
		// Wait for the connection threads to finish
		try {
			for (Connector c : connectors) c.join();
		} catch (InterruptedException e) {
			LOG.warning("Interrupted while waiting for connectors");
			Thread.currentThread().interrupt();
		}
		// If none of the threads connected, inform the listeners
		if (!connected.get()) {
			lock.lock();
			try {
				connectionFailed = true;
			} finally {
				lock.unlock();
			}
			for (InvitationListener l : listeners) l.connectionFailed();
		}
	}

	private Connector createAliceConnector(DuplexPlugin plugin,
			LocalAuthor localAuthor) {
		PseudoRandom random = crypto.getPseudoRandom(localInvitationCode,
				remoteInvitationCode);
		return new AliceConnector(crypto, bdfReaderFactory, bdfWriterFactory,
				contactExchangeTask, this, plugin, localAuthor, random);
	}

	private Connector createBobConnector(DuplexPlugin plugin,
			LocalAuthor localAuthor) {
		PseudoRandom random = crypto.getPseudoRandom(remoteInvitationCode,
				localInvitationCode);
		return new BobConnector(crypto, bdfReaderFactory, bdfWriterFactory,
				contactExchangeTask, this, plugin, localAuthor, random);
	}

	@Override
	public void localConfirmationSucceeded() {
		lock.lock();
		try {
			localCompared = true;
			localMatched = true;
		} finally {
			lock.unlock();
		}
		localConfirmationLatch.countDown();
	}

	@Override
	public void localConfirmationFailed() {
		lock.lock();
		try {
			localCompared = true;
			localMatched = false;
		} finally {
			lock.unlock();
		}
		localConfirmationLatch.countDown();
	}

	boolean getAndSetConnected() {
		boolean redundant = connected.getAndSet(true);
		if (!redundant)
			for (InvitationListener l : listeners) l.connectionSucceeded();
		return redundant;
	}

	void keyAgreementSucceeded(int localCode, int remoteCode) {
		lock.lock();
		try {
			localConfirmationCode = localCode;
			remoteConfirmationCode = remoteCode;
		} finally {
			lock.unlock();
		}
		for (InvitationListener l : listeners)
			l.keyAgreementSucceeded(localCode, remoteCode);
	}

	void keyAgreementFailed() {
		for (InvitationListener l : listeners) l.keyAgreementFailed();
	}

	boolean waitForLocalConfirmationResult() throws InterruptedException {
		localConfirmationLatch.await(CONFIRMATION_TIMEOUT, MILLISECONDS);
		lock.lock();
		try {
			return localMatched;
		} finally {
			lock.unlock();
		}
	}

	void remoteConfirmationSucceeded() {
		lock.lock();
		try {
			remoteCompared = true;
			remoteMatched = true;
		} finally {
			lock.unlock();
		}
		for (InvitationListener l : listeners) l.remoteConfirmationSucceeded();
	}

	void remoteConfirmationFailed() {
		lock.lock();
		try {
			remoteCompared = true;
			remoteMatched = false;
		} finally {
			lock.unlock();
		}
		for (InvitationListener l : listeners) l.remoteConfirmationFailed();
	}

	@Override
	public void contactExchangeSucceeded(Author remoteAuthor) {
		String name = remoteAuthor.getName();
		lock.lock();
		try {
			remoteName = name;
		} finally {
			lock.unlock();
		}
		for (InvitationListener l : listeners)
			l.pseudonymExchangeSucceeded(name);
	}

	@Override
	public void duplicateContact(Author remoteAuthor) {
		// TODO differentiate
		for (InvitationListener l : listeners) l.pseudonymExchangeFailed();
	}

	@Override
	public void contactExchangeFailed() {
		for (InvitationListener l : listeners) l.pseudonymExchangeFailed();
	}
}
