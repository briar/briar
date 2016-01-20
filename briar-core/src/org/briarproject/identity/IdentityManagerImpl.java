package org.briarproject.identity;

import com.google.inject.Inject;

import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.NoSuchLocalAuthorException;
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
import static org.briarproject.api.identity.LocalAuthor.Status.ACTIVE;
import static org.briarproject.api.identity.LocalAuthor.Status.ADDING;
import static org.briarproject.api.identity.LocalAuthor.Status.REMOVING;

class IdentityManagerImpl implements IdentityManager, Service {

	private static final Logger LOG =
			Logger.getLogger(IdentityManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final EventBus eventBus;
	private final List<IdentityAddedHook> addHooks;
	private final List<IdentityRemovedHook> removeHooks;

	@Inject
	IdentityManagerImpl(DatabaseComponent db, EventBus eventBus) {
		this.db = db;
		this.eventBus = eventBus;
		addHooks = new CopyOnWriteArrayList<IdentityAddedHook>();
		removeHooks = new CopyOnWriteArrayList<IdentityRemovedHook>();
	}

	@Override
	public boolean start() {
		// Finish adding/removing any partly added/removed pseudonyms
		try {
			for (LocalAuthor a : db.getLocalAuthors()) {
				if (a.getStatus().equals(ADDING)) {
					for (IdentityAddedHook hook : addHooks)
						hook.identityAdded(a.getId());
					db.setLocalAuthorStatus(a.getId(), ACTIVE);
					eventBus.broadcast(new LocalAuthorAddedEvent(a.getId()));
				} else if (a.getStatus().equals(REMOVING)) {
					for (IdentityRemovedHook hook : removeHooks)
						hook.identityRemoved(a.getId());
					db.removeLocalAuthor(a.getId());
					eventBus.broadcast(new LocalAuthorRemovedEvent(a.getId()));
				}
			}
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
	public void registerIdentityAddedHook(IdentityAddedHook hook) {
		addHooks.add(hook);
	}

	@Override
	public void registerIdentityRemovedHook(IdentityRemovedHook hook) {
		removeHooks.add(hook);
	}

	@Override
	public void addLocalAuthor(LocalAuthor a) throws DbException {
		db.addLocalAuthor(a);
		for (IdentityAddedHook hook : addHooks) hook.identityAdded(a.getId());
		db.setLocalAuthorStatus(a.getId(), ACTIVE);
		eventBus.broadcast(new LocalAuthorAddedEvent(a.getId()));
	}

	@Override
	public LocalAuthor getLocalAuthor(AuthorId a) throws DbException {
		LocalAuthor author = db.getLocalAuthor(a);
		if (author.getStatus().equals(ACTIVE)) return author;
		throw new NoSuchLocalAuthorException();
	}

	@Override
	public Collection<LocalAuthor> getLocalAuthors() throws DbException {
		Collection<LocalAuthor> authors = db.getLocalAuthors();
		// Filter out any pseudonyms that are being added or removed
		List<LocalAuthor> active = new ArrayList<LocalAuthor>(authors.size());
		for (LocalAuthor a : authors)
			if (a.getStatus().equals(ACTIVE)) active.add(a);
		return Collections.unmodifiableList(active);
	}

	@Override
	public void removeLocalAuthor(AuthorId a) throws DbException {
		db.setLocalAuthorStatus(a, REMOVING);
		for (IdentityRemovedHook hook : removeHooks) hook.identityRemoved(a);
		db.removeLocalAuthor(a);
		eventBus.broadcast(new LocalAuthorRemovedEvent(a));
	}
}
