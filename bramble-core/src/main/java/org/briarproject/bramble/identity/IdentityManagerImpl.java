package org.briarproject.bramble.identity;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.OpenDatabaseHook;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.Collection;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.now;

@ThreadSafe
@NotNullByDefault
class IdentityManagerImpl implements IdentityManager, OpenDatabaseHook {

	private static final Logger LOG =
			getLogger(IdentityManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final CryptoComponent crypto;
	private final AuthorFactory authorFactory;

	// The local author is immutable so we can cache it
	@Nullable
	private volatile LocalAuthor cachedAuthor;

	@Inject
	IdentityManagerImpl(DatabaseComponent db, CryptoComponent crypto,
			AuthorFactory authorFactory) {
		this.db = db;
		this.crypto = crypto;
		this.authorFactory = authorFactory;
	}

	@Override
	public LocalAuthor createLocalAuthor(String name) {
		long start = now();
		LocalAuthor localAuthor = authorFactory.createLocalAuthor(name, true);
		logDuration(LOG, "Creating local author", start);
		return localAuthor;
	}

	@Override
	public void registerLocalAuthor(LocalAuthor a) {
		cachedAuthor = a;
		LOG.info("Local author registered");
	}

	@Override
	public void onDatabaseOpened(Transaction txn) throws DbException {
		LocalAuthor cached = cachedAuthor;
		if (cached == null) {
			LocalAuthor loaded = loadLocalAuthor(txn);
			if (loaded.getHandshakePublicKey() == null) {
				KeyPair handshakeKeyPair = crypto.generateAgreementKeyPair();
				byte[] handshakePublicKey =
						handshakeKeyPair.getPublic().getEncoded();
				byte[] handshakePrivateKey =
						handshakeKeyPair.getPrivate().getEncoded();
				db.setHandshakeKeyPair(txn, loaded.getId(),
						handshakePublicKey, handshakePrivateKey);
				cachedAuthor = new LocalAuthor(loaded.getId(),
						loaded.getFormatVersion(), loaded.getName(),
						loaded.getPublicKey(), loaded.getPrivateKey(),
						handshakePublicKey, handshakePrivateKey,
						loaded.getTimeCreated());
				LOG.info("Handshake key pair added");
			} else {
				cachedAuthor = loaded;
				LOG.info("Local author loaded");
			}
		} else {
			db.addLocalAuthor(txn, cached);
			LOG.info("Local author stored");
		}
	}

	@Override
	public LocalAuthor getLocalAuthor() throws DbException {
		LocalAuthor cached = cachedAuthor;
		if (cached == null) {
			cachedAuthor = cached =
					db.transactionWithResult(true, this::loadLocalAuthor);
			LOG.info("Local author loaded");
		}
		return cached;
	}


	@Override
	public LocalAuthor getLocalAuthor(Transaction txn) throws DbException {
		LocalAuthor cached = cachedAuthor;
		if (cached == null) {
			cachedAuthor = cached = loadLocalAuthor(txn);
			LOG.info("Local author loaded");
		}
		return cached;
	}

	private LocalAuthor loadLocalAuthor(Transaction txn) throws DbException {
		Collection<LocalAuthor> authors = db.getLocalAuthors(txn);
		if (authors.size() != 1) throw new IllegalStateException();
		return authors.iterator().next();
	}
}
