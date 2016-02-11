package org.briarproject.identity;

import com.google.inject.Inject;

import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchLocalAuthorException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.LocalAuthorAddedEvent;
import org.briarproject.api.event.LocalAuthorRemovedEvent;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.lifecycle.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static org.briarproject.api.db.StorageStatus.ACTIVE;
import static org.briarproject.api.db.StorageStatus.ADDING;
import static org.briarproject.api.db.StorageStatus.REMOVING;

class IdentityManagerImpl implements IdentityManager, Service {

	private static final Logger LOG =
			Logger.getLogger(IdentityManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final EventBus eventBus;
	private final List<AddIdentityHook> addHooks;
	private final List<RemoveIdentityHook> removeHooks;

	@Inject
	IdentityManagerImpl(DatabaseComponent db, EventBus eventBus) {
		this.db = db;
		this.eventBus = eventBus;
		addHooks = new CopyOnWriteArrayList<AddIdentityHook>();
		removeHooks = new CopyOnWriteArrayList<RemoveIdentityHook>();
	}

	@Override
	public boolean start() {
		// Finish adding/removing any partly added/removed pseudonyms
		try {
			List<AuthorId> added = new ArrayList<AuthorId>();
			List<AuthorId> removed = new ArrayList<AuthorId>();
			Transaction txn = db.startTransaction();
			try {
				for (LocalAuthor a : db.getLocalAuthors(txn)) {
					if (a.getStatus().equals(ADDING)) {
						for (AddIdentityHook hook : addHooks)
							hook.addingIdentity(txn, a);
						db.setLocalAuthorStatus(txn, a.getId(), ACTIVE);
						added.add(a.getId());
					} else if (a.getStatus().equals(REMOVING)) {
						for (RemoveIdentityHook hook : removeHooks)
							hook.removingIdentity(txn, a);
						db.removeLocalAuthor(txn, a.getId());
						removed.add(a.getId());
					}
				}
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
			for (AuthorId a : added)
				eventBus.broadcast(new LocalAuthorAddedEvent(a));
			for (AuthorId a : removed)
				eventBus.broadcast(new LocalAuthorRemovedEvent(a));
			return true;
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return false;
		}
	}

	@Override
	public boolean stop() {
		return false;
	}

	@Override
	public void registerAddIdentityHook(AddIdentityHook hook) {
		addHooks.add(hook);
	}

	@Override
	public void registerRemoveIdentityHook(RemoveIdentityHook hook) {
		removeHooks.add(hook);
	}

	@Override
	public void addLocalAuthor(LocalAuthor localAuthor) throws DbException {
		Transaction txn = db.startTransaction();
		try {
			db.addLocalAuthor(txn, localAuthor);
			for (AddIdentityHook hook : addHooks)
				hook.addingIdentity(txn, localAuthor);
			db.setLocalAuthorStatus(txn, localAuthor.getId(), ACTIVE);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		eventBus.broadcast(new LocalAuthorAddedEvent(localAuthor.getId()));
	}

	@Override
	public LocalAuthor getLocalAuthor(AuthorId a) throws DbException {
		LocalAuthor author;
		Transaction txn = db.startTransaction();
		try {
			author = db.getLocalAuthor(txn, a);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		if (author.getStatus().equals(ACTIVE)) return author;
		throw new NoSuchLocalAuthorException();
	}

	@Override
	public Collection<LocalAuthor> getLocalAuthors() throws DbException {
		Collection<LocalAuthor> authors;
		Transaction txn = db.startTransaction();
		try {
			authors = db.getLocalAuthors(txn);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		// Filter out any pseudonyms that are being added or removed
		List<LocalAuthor> active = new ArrayList<LocalAuthor>(authors.size());
		for (LocalAuthor a : authors)
			if (a.getStatus().equals(ACTIVE)) active.add(a);
		return Collections.unmodifiableList(active);
	}

	@Override
	public void removeLocalAuthor(AuthorId a) throws DbException {
		Transaction txn = db.startTransaction();
		try {
			LocalAuthor localAuthor = db.getLocalAuthor(txn, a);
			db.setLocalAuthorStatus(txn, a, REMOVING);
			for (RemoveIdentityHook hook : removeHooks)
				hook.removingIdentity(txn, localAuthor);
			db.removeLocalAuthor(txn, a);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		eventBus.broadcast(new LocalAuthorRemovedEvent(a));
	}
}
