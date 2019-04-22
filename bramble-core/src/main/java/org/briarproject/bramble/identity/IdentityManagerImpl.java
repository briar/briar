package org.briarproject.bramble.identity;

import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.OpenDatabaseHook;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

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
	private final AuthorFactory authorFactory;

	// The local author is immutable so we can cache it
	@Nullable
	private volatile LocalAuthor cachedAuthor;

	@Inject
	IdentityManagerImpl(DatabaseComponent db, AuthorFactory authorFactory) {
		this.db = db;
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
			LOG.info("No local author to store");
			return;
		}
		db.addLocalAuthor(txn, cached);
		LOG.info("Local author stored");
	}

	@Override
	public LocalAuthor getLocalAuthor() throws DbException {
		if (cachedAuthor == null) {
			cachedAuthor =
					db.transactionWithResult(true, this::loadLocalAuthor);
			LOG.info("Local author loaded");
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
}
