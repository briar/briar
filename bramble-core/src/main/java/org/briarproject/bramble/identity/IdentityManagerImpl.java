package org.briarproject.bramble.identity;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Account;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.OpenDatabaseHook;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.Clock;

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
	private final Clock clock;

	@Nullable
	private volatile Account cachedAccount;

	@Inject
	IdentityManagerImpl(DatabaseComponent db, CryptoComponent crypto,
			AuthorFactory authorFactory, Clock clock) {
		this.db = db;
		this.crypto = crypto;
		this.authorFactory = authorFactory;
		this.clock = clock;
	}

	@Override
	public LocalAuthor createLocalAuthor(String name) {
		long start = now();
		LocalAuthor localAuthor = authorFactory.createLocalAuthor(name);
		logDuration(LOG, "Creating local author", start);
		return localAuthor;
	}

	@Override
	public Account createAccount(String name) {
		long start = now();
		LocalAuthor localAuthor = authorFactory.createLocalAuthor(name);
		KeyPair handshakeKeyPair = crypto.generateAgreementKeyPair();
		byte[] handshakePub = handshakeKeyPair.getPublic().getEncoded();
		byte[] handshakePriv = handshakeKeyPair.getPrivate().getEncoded();
		logDuration(LOG, "Creating account", start);
		return new Account(localAuthor, handshakePub, handshakePriv,
				clock.currentTimeMillis());
	}

	@Override
	public void registerAccount(Account a) {
		if (!a.hasHandshakeKeyPair()) throw new IllegalArgumentException();
		cachedAccount = a;
		LOG.info("Account registered");
	}

	@Override
	public void onDatabaseOpened(Transaction txn) throws DbException {
		Account cached = cachedAccount;
		if (cached == null) {
			cached = loadAccount(txn);
			if (cached.hasHandshakeKeyPair()) {
				cachedAccount = cached;
				LOG.info("Account loaded");
			} else {
				KeyPair handshakeKeyPair = crypto.generateAgreementKeyPair();
				byte[] handshakePub = handshakeKeyPair.getPublic().getEncoded();
				byte[] handshakePriv =
						handshakeKeyPair.getPrivate().getEncoded();
				db.setHandshakeKeyPair(txn, cached.getId(), handshakePub,
						handshakePriv);
				LOG.info("Handshake key pair stored");
			}
		} else {
			db.addAccount(txn, cached);
			LOG.info("Account stored");
		}
	}

	@Override
	public LocalAuthor getLocalAuthor() throws DbException {
		Account cached = cachedAccount;
		if (cached == null) {
			cachedAccount = cached =
					db.transactionWithResult(true, this::loadAccount);
			LOG.info("Account loaded");
		}
		return cached.getLocalAuthor();
	}

	@Override
	public LocalAuthor getLocalAuthor(Transaction txn) throws DbException {
		Account cached = cachedAccount;
		if (cached == null) {
			cachedAccount = cached = loadAccount(txn);
			LOG.info("Account loaded");
		}
		return cached.getLocalAuthor();
	}

	private Account loadAccount(Transaction txn) throws DbException {
		Collection<Account> accounts = db.getAccounts(txn);
		if (accounts.size() != 1) throw new DbException();
		return accounts.iterator().next();
	}
}
