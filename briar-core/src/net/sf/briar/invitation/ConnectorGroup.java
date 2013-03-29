package net.sf.briar.invitation;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.WARNING;
import static net.sf.briar.api.plugins.InvitationConstants.CONFIRMATION_TIMEOUT;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import net.sf.briar.api.Author;
import net.sf.briar.api.AuthorFactory;
import net.sf.briar.api.AuthorId;
import net.sf.briar.api.LocalAuthor;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.KeyManager;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.invitation.InvitationListener;
import net.sf.briar.api.invitation.InvitationState;
import net.sf.briar.api.invitation.InvitationTask;
import net.sf.briar.api.plugins.PluginManager;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.WriterFactory;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionWriterFactory;

/** A task consisting of one or more parallel connection attempts. */
class ConnectorGroup extends Thread implements InvitationTask {

	private static final Logger LOG =
			Logger.getLogger(ConnectorGroup.class.getName());

	private final CryptoComponent crypto;
	private final DatabaseComponent db;
	private final ReaderFactory readerFactory;
	private final WriterFactory writerFactory;
	private final ConnectionReaderFactory connectionReaderFactory;
	private final ConnectionWriterFactory connectionWriterFactory;
	private final AuthorFactory authorFactory;
	private final KeyManager keyManager;
	private final Clock clock;
	private final PluginManager pluginManager;
	private final AuthorId localAuthorId;
	private final int localInvitationCode, remoteInvitationCode;
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
	private String remoteName = null;

	ConnectorGroup(CryptoComponent crypto, DatabaseComponent db,
			ReaderFactory readerFactory, WriterFactory writerFactory,
			ConnectionReaderFactory connectionReaderFactory,
			ConnectionWriterFactory connectionWriterFactory,
			AuthorFactory authorFactory, KeyManager keyManager, Clock clock,
			PluginManager pluginManager, AuthorId localAuthorId,
			int localInvitationCode, int remoteInvitationCode) {
		super("ConnectorGroup");
		this.crypto = crypto;
		this.db = db;
		this.readerFactory = readerFactory;
		this.writerFactory = writerFactory;
		this.connectionReaderFactory = connectionReaderFactory;
		this.connectionWriterFactory = connectionWriterFactory;
		this.authorFactory = authorFactory;
		this.keyManager = keyManager;
		this.clock = clock;
		this.pluginManager = pluginManager;
		this.localAuthorId = localAuthorId;
		this.localInvitationCode = localInvitationCode;
		this.remoteInvitationCode = remoteInvitationCode;
		listeners = new CopyOnWriteArrayList<InvitationListener>();
		connected = new AtomicBoolean(false);
		localConfirmationLatch = new CountDownLatch(1);
	}

	public synchronized InvitationState addListener(InvitationListener l) {
		listeners.add(l);
		return new InvitationState(localInvitationCode, remoteInvitationCode,
				localConfirmationCode, remoteConfirmationCode,
				connectionFailed, localCompared, remoteCompared, localMatched,
				remoteMatched, remoteName);
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
			synchronized(this) {
				connectionFailed = true;
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
	}

	private Connector createAliceConnector(DuplexPlugin plugin,
			LocalAuthor localAuthor,
			Map<TransportId, TransportProperties> localProps) {
		PseudoRandom random = crypto.getPseudoRandom(localInvitationCode,
				remoteInvitationCode);
		return new AliceConnector(crypto, db, readerFactory, writerFactory,
				connectionReaderFactory, connectionWriterFactory, authorFactory,
				keyManager, clock, this, plugin, localAuthor, localProps,
				random);
	}

	private Connector createBobConnector(DuplexPlugin plugin,
			LocalAuthor localAuthor,
			Map<TransportId, TransportProperties> localProps) {
		PseudoRandom random = crypto.getPseudoRandom(remoteInvitationCode,
				localInvitationCode);
		return new BobConnector(crypto, db, readerFactory, writerFactory,
				connectionReaderFactory, connectionWriterFactory, authorFactory,
				keyManager, clock, this, plugin, localAuthor, localProps,
				random);
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

	void pseudonymExchangeSucceeded(Author remoteAuthor) {
		String name = remoteAuthor.getName();
		synchronized(this) {
			remoteName = name;
		}
		for(InvitationListener l : listeners)
			l.pseudonymExchangeSucceeded(name);
	}

	void pseudonymExchangeFailed() {
		for(InvitationListener l : listeners) l.pseudonymExchangeFailed();
	}
}
