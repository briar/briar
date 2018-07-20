package org.briarproject.bramble.identity;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author.Status;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.Collection;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static org.briarproject.bramble.api.identity.Author.Status.OURSELVES;
import static org.briarproject.bramble.api.identity.Author.Status.UNKNOWN;
import static org.briarproject.bramble.api.identity.Author.Status.UNVERIFIED;
import static org.briarproject.bramble.api.identity.Author.Status.VERIFIED;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.now;

@ThreadSafe
@NotNullByDefault
class IdentityManagerImpl implements IdentityManager {

	private static final Logger LOG =
			Logger.getLogger(IdentityManagerImpl.class.getName());

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
		KeyPair keyPair = crypto.generateSignatureKeyPair();
		byte[] publicKey = keyPair.getPublic().getEncoded();
		byte[] privateKey = keyPair.getPrivate().getEncoded();
		LocalAuthor localAuthor = authorFactory.createLocalAuthor(name,
				publicKey, privateKey);
		logDuration(LOG, "Creating local author", start);
		return localAuthor;
	}

	@Override
	public void registerLocalAuthor(LocalAuthor a) {
		cachedAuthor = a;
		LOG.info("Local author registered");
	}

	@Override
	public void storeLocalAuthor() throws DbException {
		LocalAuthor cached = cachedAuthor;
		if (cached == null) {
			LOG.info("No local author to store");
			return;
		}
		Transaction txn = db.startTransaction(false);
		try {
			db.addLocalAuthor(txn, cached);
			db.commitTransaction(txn);
			LOG.info("Local author stored");
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public LocalAuthor getLocalAuthor() throws DbException {
		if (cachedAuthor == null) {
			Transaction txn = db.startTransaction(true);
			try {
				cachedAuthor = loadLocalAuthor(txn);
				LOG.info("Local author loaded");
				db.commitTransaction(txn);
			} finally {
				db.endTransaction(txn);
			}
		}
		LocalAuthor cached = cachedAuthor;
		if (cached == null) throw new AssertionError();
		return cached;
	}


	@Override
	public LocalAuthor getLocalAuthor(Transaction txn) throws DbException {
		if (cachedAuthor == null) {
			cachedAuthor = loadLocalAuthor(txn);
			LOG.info("Local author loaded");
		}
		LocalAuthor cached = cachedAuthor;
		if (cached == null) throw new AssertionError();
		return cached;
	}

	private LocalAuthor loadLocalAuthor(Transaction txn) throws DbException {
		return db.getLocalAuthors(txn).iterator().next();
	}

	@Override
	public Status getAuthorStatus(AuthorId authorId) throws DbException {
		Transaction txn = db.startTransaction(true);
		try {
			return getAuthorStatus(txn, authorId);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public Status getAuthorStatus(Transaction txn, AuthorId authorId)
			throws DbException {
		if (getLocalAuthor(txn).getId().equals(authorId)) return OURSELVES;
		Collection<Contact> contacts = db.getContactsByAuthorId(txn, authorId);
		if (contacts.isEmpty()) return UNKNOWN;
		for (Contact c : contacts) {
			if (c.isVerified()) return VERIFIED;
		}
		return UNVERIFIED;
	}

}
