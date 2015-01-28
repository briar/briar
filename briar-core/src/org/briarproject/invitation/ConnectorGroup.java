package org.briarproject.invitation;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.invitation.InvitationConstants.CONFIRMATION_TIMEOUT;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import org.briarproject.api.Author;
import org.briarproject.api.AuthorFactory;
import org.briarproject.api.AuthorId;
import org.briarproject.api.LocalAuthor;
import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyManager;
import org.briarproject.api.crypto.PseudoRandom;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.invitation.InvitationListener;
import org.briarproject.api.invitation.InvitationState;
import org.briarproject.api.invitation.InvitationTask;
import org.briarproject.api.messaging.GroupFactory;
import org.briarproject.api.plugins.ConnectionManager;
import org.briarproject.api.plugins.PluginManager;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.serial.ReaderFactory;
import org.briarproject.api.serial.WriterFactory;
import org.briarproject.api.system.Clock;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;

/** A task consisting of one or more parallel connection attempts. */
class ConnectorGroup extends Thread implements InvitationTask {

	private static final Logger LOG =
			Logger.getLogger(ConnectorGroup.class.getName());

	private final CryptoComponent crypto;
	private final DatabaseComponent db;
	private final ReaderFactory readerFactory;
	private final WriterFactory writerFactory;
	private final StreamReaderFactory streamReaderFactory;
	private final StreamWriterFactory streamWriterFactory;
	private final AuthorFactory authorFactory;
	private final GroupFactory groupFactory;
	private final KeyManager keyManager;
	private final ConnectionManager connectionManager;
	private final Clock clock;
	private final PluginManager pluginManager;
	private final AuthorId localAuthorId;
	private final int localInvitationCode, remoteInvitationCode;
	private final boolean reuseConnection;
	private final Collection<InvitationListener> listeners;
	private final AtomicBoolean connected;
	private final CountDownLatch localConfirmationLatch;

	private final Lock synchLock = new ReentrantLock();

	/*The state that's accessed in addListener() after
	 * calling listeners.add() must be guarded by a lock.
	 */
	private int localConfirmationCode = -1, remoteConfirmationCode = -1;
	private boolean connectionFailed = false;
	private boolean localCompared = false, remoteCompared = false;
	private boolean localMatched = false, remoteMatched = false;
	private String remoteName = null;

	ConnectorGroup(CryptoComponent crypto, DatabaseComponent db,
			ReaderFactory readerFactory, WriterFactory writerFactory,
			StreamReaderFactory streamReaderFactory,
			StreamWriterFactory streamWriterFactory,
			AuthorFactory authorFactory, GroupFactory groupFactory,
			KeyManager keyManager, ConnectionManager connectionManager,
			Clock clock, PluginManager pluginManager, AuthorId localAuthorId,
			int localInvitationCode, int remoteInvitationCode,
			boolean reuseConnection) {
		super("ConnectorGroup");
		this.crypto = crypto;
		this.db = db;
		this.readerFactory = readerFactory;
		this.writerFactory = writerFactory;
		this.streamReaderFactory = streamReaderFactory;
		this.streamWriterFactory = streamWriterFactory;
		this.authorFactory = authorFactory;
		this.groupFactory = groupFactory;
		this.keyManager = keyManager;
		this.connectionManager = connectionManager;
		this.clock = clock;
		this.pluginManager = pluginManager;
		this.localAuthorId = localAuthorId;
		this.localInvitationCode = localInvitationCode;
		this.remoteInvitationCode = remoteInvitationCode;
		this.reuseConnection = reuseConnection;
		listeners = new CopyOnWriteArrayList<InvitationListener>();
		connected = new AtomicBoolean(false);
		localConfirmationLatch = new CountDownLatch(1);
	}

	public InvitationState addListener(InvitationListener l) {
		synchLock.lock();
		try {
			listeners.add(l);
			return new InvitationState(localInvitationCode,
					remoteInvitationCode, localConfirmationCode,
					remoteConfirmationCode, connected.get(), connectionFailed,
					localCompared, remoteCompared, localMatched, remoteMatched,
					remoteName);
		} finally {
			synchLock.unlock();
		}
	}

	public void removeListener(InvitationListener l) {
		listeners.remove(l);
	}

	public void connect() {
		start();
	}

	@Override
	public void run() {
		LocalAuthor localAuthor;
		Map<TransportId, TransportProperties> localProps;
		// Load the local pseudonym and transport properties
		try {
			localAuthor = db.getLocalAuthor(localAuthorId);
			localProps = db.getLocalProperties();
		} catch(DbException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			synchLock.lock();
			try {
				connectionFailed = true;
			} finally {
				synchLock.unlock();
			}
			for(InvitationListener l : listeners) l.connectionFailed();
			return;
		}
		// Start the connection threads
		Collection<Connector> connectors = new ArrayList<Connector>();
		// Alice is the party with the smaller invitation code
		if(localInvitationCode < remoteInvitationCode) {
			for(DuplexPlugin plugin : pluginManager.getInvitationPlugins()) {
				Connector c = createAliceConnector(plugin, localAuthor,
						localProps);
				connectors.add(c);
				c.start();
			}
		} else {
			for(DuplexPlugin plugin: pluginManager.getInvitationPlugins()) {
				Connector c = createBobConnector(plugin, localAuthor,
						localProps);
				connectors.add(c);
				c.start();
			}
		}
		// Wait for the connection threads to finish
		try {
			for(Connector c : connectors) c.join();
		} catch(InterruptedException e) {
			LOG.warning("Interrupted while waiting for connectors");
			Thread.currentThread().interrupt();
		}
		// If none of the threads connected, inform the listeners
		if(!connected.get()) {
			synchLock.lock();
			try {
				connectionFailed = true;
			} finally {
				synchLock.unlock();
			}
			for(InvitationListener l : listeners) l.connectionFailed();
		}
	}

	private Connector createAliceConnector(DuplexPlugin plugin,
			LocalAuthor localAuthor,
			Map<TransportId, TransportProperties> localProps) {
		PseudoRandom random = crypto.getPseudoRandom(localInvitationCode,
				remoteInvitationCode);
		return new AliceConnector(crypto, db, readerFactory, writerFactory,
				streamReaderFactory, streamWriterFactory, authorFactory,
				groupFactory, keyManager, connectionManager, clock,
				reuseConnection, this, plugin, localAuthor, localProps, random);
	}

	private Connector createBobConnector(DuplexPlugin plugin,
			LocalAuthor localAuthor,
			Map<TransportId, TransportProperties> localProps) {
		PseudoRandom random = crypto.getPseudoRandom(remoteInvitationCode,
				localInvitationCode);
		return new BobConnector(crypto, db, readerFactory, writerFactory,
				streamReaderFactory, streamWriterFactory, authorFactory,
				groupFactory, keyManager, connectionManager, clock,
				reuseConnection, this, plugin, localAuthor, localProps, random);
	}

	public void localConfirmationSucceeded() {
		synchLock.lock();
		try {
			localCompared = true;
			localMatched = true;
		} finally {
			synchLock.unlock();
		}
		localConfirmationLatch.countDown();
	}

	public void localConfirmationFailed() {
		synchLock.lock();
		try {
			localCompared = true;
			localMatched = false;
		} finally {
			synchLock.unlock();
		}
		localConfirmationLatch.countDown();
	}

	boolean getAndSetConnected() {
		boolean redundant = connected.getAndSet(true);
		if(!redundant)
			for(InvitationListener l : listeners) l.connectionSucceeded();
		return redundant;
	}

	void keyAgreementSucceeded(int localCode, int remoteCode) {
		synchLock.lock();
		try {
			localConfirmationCode = localCode;
			remoteConfirmationCode = remoteCode;
		} finally {
			synchLock.unlock();
		}
		for(InvitationListener l : listeners)
			l.keyAgreementSucceeded(localCode, remoteCode);
	}

	void keyAgreementFailed() {
		for(InvitationListener l : listeners) l.keyAgreementFailed();
	}

	boolean waitForLocalConfirmationResult() throws InterruptedException {
		localConfirmationLatch.await(CONFIRMATION_TIMEOUT, MILLISECONDS);
		synchLock.lock();
		try {
			return localMatched;
		} finally {
			synchLock.unlock();
		}
	}

	void remoteConfirmationSucceeded() {
		synchLock.lock();
		try {
			remoteCompared = true;
			remoteMatched = true;
		} finally {
			synchLock.unlock();
		}
		for(InvitationListener l : listeners) l.remoteConfirmationSucceeded();
	}

	void remoteConfirmationFailed() {
		synchLock.lock();
		try {
			remoteCompared = true;
			remoteMatched = false;
		} finally {
			synchLock.unlock();
		}
		for(InvitationListener l : listeners) l.remoteConfirmationFailed();
	}

	void pseudonymExchangeSucceeded(Author remoteAuthor) {
		String name = remoteAuthor.getName();
		synchLock.lock();
		try {
			remoteName = name;
		} finally {
			synchLock.unlock();
		}
		for(InvitationListener l : listeners)
			l.pseudonymExchangeSucceeded(name);
	}

	void pseudonymExchangeFailed() {
		for(InvitationListener l : listeners) l.pseudonymExchangeFailed();
	}
}
